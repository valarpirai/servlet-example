package com.example.scheduler;

import com.example.config.DbPropertiesLoader;
import com.example.model.Job;
import com.example.model.JobRun;
import com.example.record.TableRecord;
import com.example.repository.JobRepository;
import com.example.repository.SqlJobRepository;
import com.example.util.JsonUtil;
import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Creates, reads, changes, runs and deletes background jobs. Java code uses {@code new Jobs()}.
 * Scripts use the {@code Jobs} global. The rows live in {@code scheduled_jobs}. A job that a node
 * holds, queued or running, cannot be changed or deleted.
 */
public class Jobs {

  private static final Set<String> TRIGGERS = Set.of("once", "interval", "daily", "on_demand");

  /** Tells whether a handler exists. Jobs asks before it saves a new or changed handler. */
  @FunctionalInterface
  public interface HandlerCheck {

    /**
     * Looks for a handler.
     *
     * @param handlerType {@link Job#JAVA} or {@link Job#SCRIPT}
     * @param handler the Java key or the {@code api_scripts} name
     * @return true when the handler exists, or when this node cannot tell
     * @throws Exception when the lookup fails
     */
    boolean exists(String handlerType, String handler) throws Exception;
  }

  private final JobRepository repository;
  private final Clock clock;
  private final HandlerCheck handlerCheck;

  /** Uses the application database, found on first call. Checks that handlers exist. */
  public Jobs() {
    this(null, Clock.systemUTC(), Jobs::handlerExists);
  }

  /**
   * Uses a given repository and does not check that handlers exist.
   *
   * @param repository where jobs live, or null for the application database
   * @param clock the time source
   */
  public Jobs(JobRepository repository, Clock clock) {
    this(repository, clock, (type, handler) -> true);
  }

  /**
   * Uses a given repository and handler check.
   *
   * @param repository where jobs live, or null for the application database
   * @param clock the time source
   * @param handlerCheck says whether a handler exists
   */
  public Jobs(JobRepository repository, Clock clock, HandlerCheck handlerCheck) {
    this.repository = repository;
    this.clock = clock;
    this.handlerCheck = handlerCheck;
  }

  /**
   * Checks a job without saving it, including that its handler exists.
   *
   * @param job the job to check
   * @throws IllegalArgumentException when a field is missing or invalid, or the handler is missing
   */
  public void validate(Job job) {
    check(job);
    requireHandler(job);
  }

  /** Checks the fields. Does not look for the handler. */
  private void check(Job job) {
    if (isBlank(job.name)) {
      throw new IllegalArgumentException("Job needs a name");
    }
    if (!Job.JAVA.equals(job.handlerType) && !Job.SCRIPT.equals(job.handlerType)) {
      throw new IllegalArgumentException("handlerType must be 'java' or 'script'");
    }
    if (isBlank(job.handler)) {
      throw new IllegalArgumentException("Job needs a handler");
    }
    if (!TRIGGERS.contains(job.triggerType)) {
      throw new IllegalArgumentException("Unknown triggerType: " + job.triggerType);
    }
    if (!"on_demand".equals(job.triggerType)) {
      Recurrences.validate(job);
    }
    if (job.maxRetries < 0 || job.retryDelaySeconds <= 0 || job.retryMaxDelaySeconds <= 0) {
      throw new IllegalArgumentException("Retry counts and delays must be positive");
    }
    if (job.retryJitter < 0 || job.retryJitter > 1) {
      throw new IllegalArgumentException("retryJitter must be between 0 and 1");
    }
  }

  /**
   * Checks a job given as a script object, without saving it.
   *
   * @param fields job fields by bean name
   * @throws IllegalArgumentException when a field is missing or invalid, or the handler is missing
   */
  public void validate(Map<String, Object> fields) {
    validate(apply(blank(), fields));
  }

  /**
   * Validates and saves a job. The first run is at {@code nextRun}, or now.
   *
   * @param job the job; its id is replaced
   * @return the new id
   */
  public String create(Job job) {
    validate(job);
    job.id = UUID.randomUUID().toString();
    try {
      repository().insert(job, clock.instant());
    } catch (Exception e) {
      throw new IllegalStateException("Creating the job failed: " + e.getMessage(), e);
    }
    wake();
    return job.id;
  }

  /**
   * Validates and saves a job given as a script object.
   *
   * @param fields name, handlerType, handler, triggerType, triggerSpec, nextRun, priority, context,
   *     runEnd, maxRetries, retryDelaySeconds, retryExponential, retryMaxDelaySeconds, retryJitter
   * @return the new id
   */
  public String create(Map<String, Object> fields) {
    Job job = blank();
    job.triggerType = "once";
    return create(apply(job, fields));
  }

  /**
   * Queues a one-time job to run now.
   *
   * @param name label for logs and history
   * @param handlerType {@code java} or {@code script}
   * @param handler a Java key, or an {@code api_scripts} name
   * @param context input for the handler, or null
   * @return the job id
   */
  public String enqueue(
      String name, String handlerType, String handler, Map<String, Object> context) {
    Job job = blank();
    job.name = name;
    job.handlerType = handlerType;
    job.handler = handler;
    job.triggerType = "once";
    job.context = json(context);
    return create(job);
  }

  /**
   * Queues a one-time copy of a job to run now. The job keeps its own schedule.
   *
   * @param id the job id
   * @return the id of the new one-time job
   * @throws IllegalArgumentException when no job has the id
   */
  public String runNow(String id) {
    Job source = load(id);
    Job copy = blank();
    copy.name = source.name;
    copy.handlerType = source.handlerType;
    copy.handler = source.handler;
    copy.context = source.context.deepCopy();
    copy.priority = source.priority;
    copy.maxRetries = source.maxRetries;
    copy.retryDelaySeconds = source.retryDelaySeconds;
    copy.retryExponential = source.retryExponential;
    copy.retryMaxDelaySeconds = source.retryMaxDelaySeconds;
    copy.retryJitter = source.retryJitter;
    copy.triggerType = "once";
    return create(copy);
  }

  /**
   * Reads one job.
   *
   * @param id the job id
   * @return the job, or null when none has the id
   */
  public JobInfo get(String id) {
    try {
      Job job = repository().find(id);
      return job == null ? null : JobInfo.of(job);
    } catch (Exception e) {
      throw new IllegalStateException("Reading the job failed: " + e.getMessage(), e);
    }
  }

  /**
   * Lists every job, soonest due first.
   *
   * @return the jobs, possibly empty
   */
  public List<JobInfo> list() {
    return list(null);
  }

  /**
   * Lists jobs in one state.
   *
   * @param state 0 ready, 1 queued, 2 running, 3 error
   * @return the jobs, possibly empty
   */
  public List<JobInfo> listByState(int state) {
    return list(state);
  }

  /**
   * Changes fields of a job. The result must still be valid. The job returns to ready, with its
   * last error and retry count cleared, so editing is also how an errored job is retried. The
   * handler is looked up only when the change sets a different handler type or handler.
   *
   * @param id the job id
   * @param changes fields to change, by bean name
   * @return true when saved, false when a node holds the job
   * @throws IllegalArgumentException when no job has the id or the result is invalid
   */
  public boolean update(String id, Map<String, Object> changes) {
    Job job = load(id);
    String oldType = job.handlerType;
    String oldHandler = job.handler;
    apply(job, changes);
    check(job);
    if (!Objects.equals(oldType, job.handlerType) || !Objects.equals(oldHandler, job.handler)) {
      requireHandler(job);
    }
    try {
      boolean saved = repository().update(job, clock.instant());
      if (saved) {
        wake();
      }
      return saved;
    } catch (Exception e) {
      throw new IllegalStateException("Updating the job failed: " + e.getMessage(), e);
    }
  }

  /**
   * Deletes a job.
   *
   * @param id the job id
   * @return true when deleted, false when no job has the id or a node holds it
   */
  public boolean delete(String id) {
    try {
      return repository().remove(id);
    } catch (Exception e) {
      throw new IllegalStateException("Deleting the job failed: " + e.getMessage(), e);
    }
  }

  /**
   * Reads run history, newest first. History outlives a deleted job.
   *
   * @param id the job id
   * @param limit most runs to return
   * @return the runs, possibly empty
   */
  public List<JobRun> history(String id, int limit) {
    try {
      return repository().runs(id, Math.max(1, limit));
    } catch (Exception e) {
      throw new IllegalStateException("Reading the history failed: " + e.getMessage(), e);
    }
  }

  // ---- internals ----

  private void requireHandler(Job job) {
    boolean exists;
    try {
      exists = handlerCheck.exists(job.handlerType, job.handler);
    } catch (Exception e) {
      throw new IllegalStateException("Looking for the handler failed: " + e.getMessage(), e);
    }
    if (!exists) {
      throw new IllegalArgumentException(
          Job.SCRIPT.equals(job.handlerType)
              ? "No api_scripts row named '" + job.handler + "'"
              : "No Java handler registered under '" + job.handler + "'");
    }
  }

  /**
   * The check {@code new Jobs()} uses. A script handler must be a row of {@code api_scripts}. A
   * Java handler must be registered, which only a running scheduler can say. Where none runs, the
   * answer is yes.
   */
  private static boolean handlerExists(String handlerType, String handler) {
    if (Job.SCRIPT.equals(handlerType)) {
      TableRecord scripts = new TableRecord("api_scripts");
      scripts.addQuery("name", handler);
      scripts.setLimit(1);
      scripts.query();
      return scripts.next();
    }
    Scheduler running = Scheduler.get();
    return running == null || running.hasJavaHandler(handler);
  }

  private JobRepository repository() {
    if (repository != null) {
      return repository;
    }
    return new SqlJobRepository(DbPropertiesLoader.getDatabase());
  }

  private List<JobInfo> list(Integer state) {
    try {
      List<JobInfo> out = new ArrayList<>();
      for (Job job : repository().list(state)) {
        out.add(JobInfo.of(job));
      }
      return out;
    } catch (Exception e) {
      throw new IllegalStateException("Listing jobs failed: " + e.getMessage(), e);
    }
  }

  private Job load(String id) {
    try {
      Job job = repository().find(id);
      if (job == null) {
        throw new IllegalArgumentException("No job with id " + id);
      }
      return job;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Reading the job failed: " + e.getMessage(), e);
    }
  }

  /** Wakes the scheduler so a new or edited job is seen now, when one runs on this node. */
  private static void wake() {
    Scheduler running = Scheduler.get();
    if (running != null) {
      running.wakeup();
    }
  }

  private static Job blank() {
    return new Job();
  }

  /** Copies the named fields onto the job. An unknown name is an error. */
  private static Job apply(Job job, Map<String, Object> fields) {
    Map<String, Object> left = new LinkedHashMap<>(fields);
    take(left, "name", v -> job.name = text(v));
    take(left, "handlerType", v -> job.handlerType = text(v));
    take(left, "handler", v -> job.handler = text(v));
    take(left, "triggerType", v -> job.triggerType = text(v));
    take(left, "triggerSpec", v -> job.triggerSpec = json(v));
    take(left, "nextRun", v -> job.nextRun = v == null ? null : Instant.parse(text(v)));
    take(left, "priority", v -> job.priority = number(v).intValue());
    take(left, "context", v -> job.context = json(v));
    take(left, "runEnd", v -> job.runEnd = v == null ? null : Instant.parse(text(v)));
    take(left, "maxRetries", v -> job.maxRetries = number(v).intValue());
    take(left, "retryDelaySeconds", v -> job.retryDelaySeconds = number(v).intValue());
    take(left, "retryExponential", v -> job.retryExponential = flag(v));
    take(left, "retryMaxDelaySeconds", v -> job.retryMaxDelaySeconds = number(v).intValue());
    take(left, "retryJitter", v -> job.retryJitter = number(v).doubleValue());
    if (!left.isEmpty()) {
      throw new IllegalArgumentException("Unknown job fields: " + left.keySet());
    }
    return job;
  }

  private static void take(Map<String, Object> fields, String name, Consumer<Object> setter) {
    if (fields.containsKey(name)) {
      setter.accept(fields.remove(name));
    }
  }

  /** Turns a script object, a Java map or JSON text into a JSON object. Null means empty. */
  private static JsonObject json(Object value) {
    if (value == null) {
      return new JsonObject();
    }
    String text =
        value instanceof CharSequence ? value.toString() : JsonUtil.toJson(wholeNumbers(value));
    try {
      return JsonUtil.fromJson(text, JsonObject.class);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Not a JSON object: " + text, e);
    }
  }

  /** Script numbers are doubles. A whole one becomes a long, so 3600 is stored as 3600. */
  private static Object wholeNumbers(Object value) {
    if (value instanceof Double d && d == Math.rint(d) && !Double.isInfinite(d)) {
      return d.longValue();
    }
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> out = new LinkedHashMap<>();
      map.forEach((k, v) -> out.put(k, wholeNumbers(v)));
      return out;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(Jobs::wholeNumbers).toList();
    }
    return value;
  }

  private static Number number(Object v) {
    if (v instanceof Number n) {
      return n;
    }
    try {
      return Double.parseDouble(text(v));
    } catch (NumberFormatException | NullPointerException e) {
      throw new IllegalArgumentException("Not a number: " + v);
    }
  }

  private static boolean flag(Object v) {
    return v instanceof Boolean b ? b : Boolean.parseBoolean(text(v));
  }

  private static String text(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
