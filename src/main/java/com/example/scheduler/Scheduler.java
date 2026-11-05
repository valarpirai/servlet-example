package com.example.scheduler;

import com.example.util.StructuredLogger;
import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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

  private final JobRepository repository;
  private final HandlerRegistry handlers;
  private final Clock clock;
  private final String node;
  private final SchedulerConfig config;
  private final JobQueue queue = new JobQueue();

  /** Ids of jobs this node holds: queued plus running. */
  private final Set<String> held = ConcurrentHashMap.newKeySet();

  private final AtomicLong ran = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();
  private final AtomicLong lostRaces = new AtomicLong();
  private final AtomicLong recovered = new AtomicLong();
  private final Object wake = new Object();
  private final List<Thread> workers = new ArrayList<>();
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
      recovered.addAndGet(repository.recoverStuck(node, Set.of(), now, now));
    } catch (Exception e) {
      logger.warn("Startup recovery failed: {}", e.getMessage());
    }
    running = true;
    for (int i = 0; i < config.workers; i++) {
      Thread t = new Thread(this::workerLoop, "scheduler-worker-" + i);
      t.setDaemon(true);
      workers.add(t);
      t.start();
    }
    loop = new Thread(this::schedulerLoop, "scheduler");
    loop.setDaemon(true);
    loop.start();
    slog.info("Scheduler started", Map.of("node", node, "workers", config.workers));
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
    for (Thread t : workers) {
      join(t, deadline);
    }
    for (Thread t : workers) {
      if (t.isAlive()) {
        t.interrupt();
        join(t, System.currentTimeMillis() + 2000);
      }
    }
    workers.clear();
    slog.info("Scheduler stopped", Map.<String, Object>of("handedBack", queued.size()));
  }

  /** Counters since start. */
  public Map<String, Object> stats() {
    return Map.of(
        "queued", queue.size(),
        "held", held.size(),
        "ran", ran.get(),
        "failed", failed.get(),
        "lostRaces", lostRaces.get(),
        "recovered", recovered.get());
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
    for (Job job : repository.claim(room, node, clock.instant())) {
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
      recovered.addAndGet(reset);
      slog.warn("Sweep reset stuck jobs", Map.<String, Object>of("count", reset));
    }
  }

  private void workerLoop() {
    while (true) {
      Job job;
      try {
        job = queue.take();
      } catch (InterruptedException e) {
        return;
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
    if (!repository.acquire(job, node, runId, clock.instant())) {
      lostRaces.incrementAndGet();
      return;
    }
    try {
      Recurrences.validate(job);
    } catch (IllegalArgumentException e) {
      repository.park(job, runId, e.getMessage(), clock.instant());
      slog.warn("Job parked", Map.of("job", job.name, "error", e.getMessage()));
      return;
    }
    JobHandler handler = handlers.find(job.handler);
    if (handler == null) {
      Instant later = clock.instant().plusMillis(config.missingHandlerDelayMillis);
      repository.reschedule(
          job, runId, later, job.context, "no handler: " + job.handler, clock.instant());
      return;
    }
    JsonObject context = job.context;
    String error = null;
    try {
      JsonObject out = handler.execute(job.context);
      if (out != null) {
        context = out;
      }
      ran.incrementAndGet();
    } catch (Throwable t) { // handler code is untrusted; record and carry on
      error = t.toString();
      failed.incrementAndGet();
      slog.warn("Job failed", Map.of("job", job.name, "error", error));
    }
    finish(job, runId, context, error);
  }

  /** One write: next due time, context, error and owner together. */
  private void finish(Job job, String runId, JsonObject context, String error) throws Exception {
    Instant now = clock.instant();
    Optional<Instant> next = Recurrences.forType(job.triggerType).next(job, now);
    if (next.isPresent() && job.runEnd != null && next.get().isAfter(job.runEnd)) {
      next = Optional.empty();
    }
    if (next.isPresent()) {
      repository.reschedule(job, runId, next.get(), context, error, now);
    } else if (error == null) {
      repository.delete(job, runId);
    } else {
      repository.fail(job, runId, error, context, now);
    }
  }
}
