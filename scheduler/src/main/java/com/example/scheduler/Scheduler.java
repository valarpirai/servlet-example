package com.example.scheduler;

import com.example.repository.JobRepository;
import com.example.util.StructuredLogger;
import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs jobs stored in {@code scheduled_jobs}. One scheduler thread claims due jobs into a {@link
 * JobQueue}. Worker threads take jobs from the queue, run them and write the next due time back.
 * The table is the truth. A crash loses only memory, and the sweep puts stranded rows back.
 */
public class Scheduler {

  private static final Logger logger = LoggerFactory.getLogger(Scheduler.class);
  private static final StructuredLogger slog = StructuredLogger.create(logger);

  private static volatile Scheduler instance;

  private final JobRepository repository;
  private final HandlerRegistry handlers;
  private final Clock clock;
  private final String node;
  private final SchedulerConfig config;
  private final JobQueue queue = new JobQueue();

  /** Ids of jobs this node holds: queued plus running. */
  private final Set<String> held = ConcurrentHashMap.newKeySet();

  private final SchedulerMetrics metrics = new SchedulerMetrics();
  private final AtomicInteger active = new AtomicInteger();

  /** Source of retry jitter, from 0 up to 1. Tests replace it. */
  private DoubleSupplier random = () -> ThreadLocalRandom.current().nextDouble();

  private final Object wake = new Object();
  private final List<Worker> workers = new ArrayList<>();
  private int nextWorkerIndex;

  /** Most worker threads reload will start, whatever the setting says. */
  static final int MAX_WORKERS = 64;

  private Thread loop;
  private volatile boolean running;
  private boolean woken;

  /**
   * Creates a scheduler. Call {@link #start} to run it.
   *
   * @param repository the jobs table
   * @param handlers maps handler keys to code
   * @param clock source of time, so tests control it
   * @param node id of this node; stable across restarts so a restart recovers its own rows
   * @param config settings
   */
  public Scheduler(
      JobRepository repository,
      HandlerRegistry handlers,
      Clock clock,
      String node,
      SchedulerConfig config) {
    this.repository = repository;
    this.handlers = handlers;
    this.clock = clock;
    this.node = node;
    this.config = config;
  }

  /** Resets rows a previous run of this node left behind, then starts the threads. */
  public synchronized void start() {
    if (running) {
      return;
    }
    try {
      Instant now = clock.instant();
      metrics.recovered(repository.recoverStuck(node, Set.of(), now, now));
    } catch (Exception e) {
      logger.warn("Startup recovery failed: {}", e.getMessage());
    }
    running = true;
    instance = this;
    for (int i = 0; i < clampWorkers(config.workers); i++) {
      addWorker();
    }
    loop = new Thread(this::schedulerLoop, "bg-scheduler");
    loop.setDaemon(true);
    loop.start();
    slog.info("Scheduler started", Map.of("node", node, "workers", config.workers));
  }

  /**
   * Returns the running scheduler.
   *
   * @return the scheduler started by {@link #start}, or null when none runs (for example when
   *     {@code scheduler.enabled} is false)
   */
  public static Scheduler get() {
    return instance;
  }

  /**
   * Queues a one-time job to run now, then wakes the scheduler.
   *
   * @param name label for logs
   * @param handlerType {@link Job#JAVA} or {@link Job#SCRIPT}
   * @param handler a registered Java key, or an {@code api_scripts} name
   * @param context input for the handler; null means empty
   * @return id of the new job row
   * @throws Exception when the insert fails
   */
  public String enqueue(String name, String handlerType, String handler, JsonObject context)
      throws Exception {
    Job job = new Job();
    job.name = name;
    job.handlerType = handlerType;
    job.handler = handler;
    if (context != null) {
      job.context = context;
    }
    return enqueue(job);
  }

  /**
   * Queues a one-time job built by the caller, for example one with retry settings. The scheduler
   * sets the id and the {@code once} trigger. Every other field keeps the caller's value.
   *
   * @param job the job; {@code name}, {@code handlerType} and {@code handler} are required
   * @return id of the new job row
   * @throws Exception when the insert fails
   */
  public String enqueue(Job job) throws Exception {
    job.id = UUID.randomUUID().toString();
    job.triggerType = "once";
    repository.insert(job, clock.instant());
    wakeup();
    return job.id;
  }

  /**
   * Tells whether a Java handler is registered on this node.
   *
   * @param key the {@code handler} value of a Java job
   * @return true when a handler runs under that key
   */
  public boolean hasJavaHandler(String key) {
    return handlers.find(Job.JAVA, key) != null;
  }

  /** Replaces the jitter source. For tests. */
  void setRandom(DoubleSupplier random) {
    this.random = random;
  }

  /** Checks the table now instead of waiting for the next poll. */
  public void wakeup() {
    synchronized (wake) {
      woken = true;
      wake.notifyAll();
    }
  }

  /**
   * Stops claiming, hands queued jobs back, and gives running jobs time to finish. Jobs still
   * running after the wait are interrupted.
   */
  public synchronized void stop() {
    if (!running) {
      return;
    }
    running = false;
    if (instance == this) {
      instance = null;
    }
    wakeup();
    queue.close();
    long deadline = System.currentTimeMillis() + config.stopMillis;
    join(loop, deadline);
    List<Job> queued = queue.drain();
    try {
      repository.release(queued, node, clock.instant());
    } catch (Exception e) {
      logger.warn("Could not hand back {} queued jobs: {}", queued.size(), e.getMessage());
    }
    queued.forEach(j -> held.remove(j.id));
    for (Worker w : workers) {
      join(w.thread, deadline);
    }
    for (Worker w : workers) {
      if (w.thread.isAlive()) {
        w.thread.interrupt();
        join(w.thread, System.currentTimeMillis() + 2000);
      }
    }
    workers.clear();
    slog.info("Scheduler stopped", Map.<String, Object>of("handedBack", queued.size()));
  }

  /**
   * What this node did since start, from memory. Cheap enough to call on every metrics report.
   *
   * @return node id, queue depth, running jobs, counters, claim time, lateness, per-handler times
   */
  public Map<String, Object> stats() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("node", node);
    out.put("queued", queue.size());
    out.put("running", active.get());
    out.put("workers", workerCount());
    out.put("held", held.size());
    out.putAll(metrics.snapshot());
    return out;
  }

  /**
   * Counts job rows by state across the whole cluster. Reads the table.
   *
   * @return state name to row count; every state is present, with zero when empty
   * @throws Exception when the query fails
   */
  public Map<String, Long> tableCounts() throws Exception {
    Map<Integer, Long> counts = repository.countByState();
    Map<String, Long> out = new LinkedHashMap<>();
    out.put("ready", counts.getOrDefault(Job.READY, 0L));
    out.put("queued", counts.getOrDefault(Job.QUEUED, 0L));
    out.put("running", counts.getOrDefault(Job.RUNNING, 0L));
    out.put("error", counts.getOrDefault(Job.ERROR, 0L));
    return out;
  }

  private static void join(Thread t, long deadline) {
    if (t == null) {
      return;
    }
    try {
      t.join(Math.max(1, deadline - System.currentTimeMillis()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void schedulerLoop() {
    long lastSweep = System.currentTimeMillis();
    while (running) {
      try {
        tick();
        if (System.currentTimeMillis() - lastSweep >= config.sweepMillis) {
          lastSweep = System.currentTimeMillis();
          sweep();
        }
      } catch (Throwable t) { // a dead scheduler thread stops every job
        logger.warn("Scheduler tick failed: {}", t.toString());
      }
      synchronized (wake) {
        if (running && !woken) {
          try {
            wake.wait(config.pollMillis);
          } catch (InterruptedException e) {
            return;
          }
        }
        woken = false;
      }
    }
  }

  /** Claims due jobs, never holding more than {@code maxInFlight}. Visible for tests. */
  void tick() throws Exception {
    int room = Math.min(config.claimBatch, config.maxInFlight - held.size());
    if (room <= 0) {
      return;
    }
    long began = System.nanoTime();
    List<Job> claimed = repository.claim(room, node, clock.instant());
    metrics.claim(claimed.size(), (System.nanoTime() - began) / 1_000_000);
    for (Job job : claimed) {
      held.add(job.id);
      queue.offer(job);
    }
  }

  /** Resets rows this node owns in queued or running state but holds nowhere. */
  void sweep() throws Exception {
    Instant now = clock.instant();
    int reset =
        repository.recoverStuck(
            node, Set.copyOf(held), now.minusMillis(config.sweepGraceMillis), now);
    if (reset > 0) {
      metrics.recovered(reset);
      slog.warn("Sweep reset stuck jobs", Map.<String, Object>of("count", reset));
    }
    repository.purgeRuns(now.minusMillis(config.historyMillis));
  }

  /** One worker thread. Retiring ends it after its current job. */
  private static final class Worker {
    final int index;
    Thread thread;

    /** Set before the interrupt that wakes an idle worker. */
    volatile boolean retire;

    /** True while the worker waits for a job. Guarded by the worker's own lock. */
    boolean waiting;

    Worker(int index) {
      this.index = index;
    }
  }

  private void addWorker() {
    Worker w = new Worker(nextWorkerIndex++);
    w.thread = new Thread(() -> workerLoop(w), "bg-scheduler-worker-" + w.index);
    w.thread.setDaemon(true);
    workers.add(w);
    w.thread.start();
  }

  private static int clampWorkers(int wanted) {
    return Math.max(1, Math.min(MAX_WORKERS, wanted));
  }

  /** Worker threads that are not retiring. */
  int workerCount() {
    synchronized (this) {
      return (int) workers.stream().filter(w -> !w.retire).count();
    }
  }

  /**
   * Reads the {@code scheduler.*} settings again and applies them to the running scheduler. Adds
   * worker threads, or retires extras: an idle one stops at once, a busy one after its job. Poll,
   * claim and sweep settings change at once. {@code scheduler.enabled} is not read here.
   *
   * @return what changed, or a note when nothing did or the scheduler is stopped
   */
  public String reload() {
    com.example.util.PropertiesUtil.reload();
    return reload(SchedulerConfig.fromProperties());
  }

  /** Applies settings. Visible for tests. */
  synchronized String reload(SchedulerConfig fresh) {
    if (!running) {
      return "The scheduler is not running";
    }
    int oldWorkers = workerCount();
    int target = clampWorkers(fresh.workers);
    workers.removeIf(w -> w.retire && !w.thread.isAlive());
    for (int i = oldWorkers; i < target; i++) {
      addWorker();
    }
    if (target < oldWorkers) {
      List<Worker> live = workers.stream().filter(w -> !w.retire).toList();
      for (int i = live.size() - 1; i >= target; i--) {
        retire(live.get(i));
      }
    }
    config.maxInFlight = fresh.maxInFlight;
    config.claimBatch = fresh.claimBatch;
    config.pollMillis = fresh.pollMillis;
    config.sweepMillis = fresh.sweepMillis;
    config.historyMillis = fresh.historyMillis;
    config.workers = target;
    wakeup();
    String summary =
        "workers "
            + oldWorkers
            + " -> "
            + target
            + ", maxInFlight "
            + config.maxInFlight
            + ", claimBatch "
            + config.claimBatch
            + ", pollMillis "
            + config.pollMillis;
    slog.info("Scheduler settings reloaded", Map.<String, Object>of("applied", summary));
    return summary;
  }

  private static void retire(Worker w) {
    w.retire = true;
    synchronized (w) {
      if (w.waiting) {
        w.thread.interrupt();
      }
    }
  }

  private void workerLoop(Worker w) {
    while (!w.retire) {
      Job job;
      try {
        synchronized (w) {
          w.waiting = true;
        }
        job = queue.take();
      } catch (InterruptedException e) {
        return;
      } finally {
        synchronized (w) {
          w.waiting = false;
          if (w.retire) {
            Thread.interrupted(); // clear a retire interrupt that came as a job arrived
          }
        }
      }
      if (job == null) {
        return; // closed
      }
      // A job taken here is ours to run or hand back. Never test "running" before running it.
      try {
        run(job);
      } catch (Throwable t) { // a dead worker shrinks the pool for good
        logger.warn("Job {} crashed the worker step: {}", job.id, t.toString());
      } finally {
        held.remove(job.id);
      }
    }
  }

  private void run(Job job) throws Exception {
    String runId = UUID.randomUUID().toString();
    Instant startedAt = clock.instant();
    if (!repository.acquire(job, node, runId, startedAt)) {
      metrics.lostRace();
      return;
    }
    active.incrementAndGet();
    long began = System.nanoTime();
    try {
      metrics.started(job, startedAt);
      try {
        Recurrences.validate(job);
      } catch (IllegalArgumentException e) {
        repository.park(job, runId, e.getMessage(), clock.instant());
        slog.warn("Job parked", Map.of("job", job.name, "error", e.getMessage()));
        record(job, runId, JobRun.PARKED, e.getMessage(), startedAt, began);
        return;
      }
      JobHandler handler = handlers.find(job.handlerType, job.handler);
      if (handler == null) {
        String error = "no handler: " + job.handlerType + " " + job.handler;
        Instant later = clock.instant().plusMillis(config.missingHandlerDelayMillis);
        repository.reschedule(
            job, runId, later, job.context, error, job.retryCount, clock.instant());
        record(job, runId, JobRun.NO_HANDLER, error, startedAt, began);
        return;
      }
      JsonObject context = job.context;
      String error = null;
      try {
        JsonObject out = handler.execute(job.context);
        if (out != null) {
          context = out;
        }
      } catch (Throwable t) { // handler code is untrusted; record and carry on
        error = t.toString();
        slog.warn("Job failed", Map.of("job", job.name, "error", error));
      }
      metrics.finished(
          job.handlerType + ":" + job.handler,
          (System.nanoTime() - began) / 1_000_000,
          error == null);
      finish(job, runId, context, error);
      record(job, runId, error == null ? JobRun.SUCCESS : JobRun.FAILED, error, startedAt, began);
    } finally {
      active.decrementAndGet();
    }
  }

  /** Writes the history row. A failure here is logged and never fails the job. */
  private void record(
      Job job, String runId, String status, String error, Instant startedAt, long beganNanos) {
    JobRun run = new JobRun();
    run.jobId = job.id;
    run.jobName = job.name;
    run.handlerType = job.handlerType;
    run.handler = job.handler;
    run.node = node;
    run.runId = runId;
    run.status = status;
    run.error = error;
    run.startedAt = startedAt;
    run.endedAt = clock.instant();
    run.durationMillis = (System.nanoTime() - beganNanos) / 1_000_000;
    try {
      repository.recordRun(run);
    } catch (Exception e) {
      logger.warn("Could not record run of job {}: {}", job.id, e.getMessage());
    }
  }

  /** One write: next due time, context, error and owner together. */
  private void finish(Job job, String runId, JsonObject context, String error) throws Exception {
    Instant now = clock.instant();
    if (error != null && job.retryCount < job.maxRetries) {
      int attempt = job.retryCount + 1;
      Instant retryAt = now.plusMillis(RetryPolicy.delayMillis(job, attempt, random.getAsDouble()));
      if (job.runEnd == null || !retryAt.isAfter(job.runEnd)) {
        repository.reschedule(job, runId, retryAt, context, error, attempt, now);
        metrics.retried();
        slog.info(
            "Job will retry",
            Map.of(
                "job",
                job.name,
                "attempt",
                attempt,
                "of",
                job.maxRetries,
                "at",
                retryAt.toString()));
        return;
      }
    }
    Optional<Instant> next = Recurrences.forType(job.triggerType).next(job, now);
    if (next.isPresent() && job.runEnd != null && next.get().isAfter(job.runEnd)) {
      next = Optional.empty();
    }
    if (next.isPresent()) {
      repository.reschedule(job, runId, next.get(), context, error, 0, now);
    } else if (error == null) {
      repository.delete(job, runId);
    } else {
      repository.fail(job, runId, error, context, now);
    }
  }
}
