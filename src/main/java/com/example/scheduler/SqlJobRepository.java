package com.example.scheduler;

import com.example.config.Database;
import com.example.record.TableRecord;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link JobRepository} on the {@code scheduled_jobs} and {@code scheduled_job_runs} tables, read
 * and written through {@link TableRecord}. Both tables must be on {@code record.allowedTables}.
 * Times are stored in UTC.
 *
 * <p>Every state change uses {@link TableRecord#updateMultiple}, which repeats its query conditions
 * in the write. A row that changed in between is skipped, so the returned count is the lock.
 */
public class SqlJobRepository implements JobRepository {

  private static final String JOBS = "scheduled_jobs";
  private static final String RUNS = "scheduled_job_runs";

  private final Database database;

  public SqlJobRepository(Database database) {
    this.database = database;
  }

  @Override
  public void recordRun(JobRun run) {
    TableRecord r = new TableRecord(database, RUNS);
    r.setValue("job_id", run.jobId);
    r.setValue("job_name", run.jobName);
    r.setValue("handler_type", run.handlerType);
    r.setValue("handler", run.handler);
    r.setValue("node", run.node);
    r.setValue("run_id", run.runId);
    r.setValue("status", run.status);
    r.setValue("error", run.error);
    r.setValue("started_at", utc(run.startedAt));
    r.setValue("ended_at", utc(run.endedAt));
    r.setValue("duration_ms", run.durationMillis);
    r.insert();
  }

  @Override
  public int purgeRuns(Instant before) {
    TableRecord r = new TableRecord(database, RUNS);
    r.addQuery("started_at", "<", utc(before));
    return r.deleteMultiple();
  }

  @Override
  public Map<Integer, Long> countByState() {
    Map<Integer, Long> counts = new HashMap<>();
    for (int state : new int[] {Job.READY, Job.QUEUED, Job.RUNNING, Job.ERROR}) {
      TableRecord r = new TableRecord(database, JOBS);
      r.addQuery("state", state);
      counts.put(state, (long) r.count());
    }
    return counts;
  }

  @Override
  public void insert(Job job, Instant now) {
    TableRecord r = new TableRecord(database, JOBS);
    r.setValue("id", job.id);
    r.setValue("name", job.name);
    r.setValue("handler_type", job.handlerType);
    r.setValue("handler", job.handler);
    r.setValue("trigger_type", job.triggerType);
    r.setValue("trigger_spec", job.triggerSpec.toString());
    r.setValue("next_run", utc(job.nextRun != null ? job.nextRun : now));
    r.setValue("state", Job.READY);
    r.setValue("priority", job.priority);
    r.setValue("context", job.context.toString());
    r.setValue("max_retries", job.maxRetries);
    r.setValue("retry_delay_seconds", job.retryDelaySeconds);
    r.setValue("retry_exponential", job.retryExponential);
    r.setValue("retry_max_delay_seconds", job.retryMaxDelaySeconds);
    r.setValue("retry_jitter", BigDecimal.valueOf(job.retryJitter));
    if (job.runEnd != null) {
      r.setValue("run_end", utc(job.runEnd));
    }
    r.setValue("updated_at", utc(now));
    r.insert();
  }

  @Override
  public List<Job> claim(int limit, String node, Instant now) {
    TableRecord due = new TableRecord(database, JOBS);
    due.addQuery("state", Job.READY);
    due.addQuery("trigger_type", "!=", "on_demand");
    due.addQuery("next_run", "<=", utc(now));
    due.orderBy("priority");
    due.orderBy("next_run");
    due.setLimit(limit);
    due.query();
    List<Job> claimed = new ArrayList<>();
    while (due.next()) {
      Job job = toJob(due);
      TableRecord won = new TableRecord(database, JOBS);
      won.addQuery("id", job.id);
      won.addQuery("state", Job.READY);
      won.setValue("state", Job.QUEUED);
      won.setValue("owner", node);
      won.setValue("updated_at", utc(now));
      if (won.updateMultiple() == 1) {
        job.state = Job.QUEUED;
        job.owner = node;
        claimed.add(job);
      }
    }
    return claimed;
  }

  @Override
  public boolean acquire(Job job, String node, String runId, Instant now) {
    TableRecord r = new TableRecord(database, JOBS);
    r.addQuery("id", job.id);
    r.addQuery("state", Job.QUEUED);
    r.addQuery("owner", node);
    r.setValue("state", Job.RUNNING);
    r.setValue("run_id", runId);
    r.setValue("updated_at", utc(now));
    return r.updateMultiple() == 1;
  }

  @Override
  public void reschedule(
      Job job,
      String runId,
      Instant nextRun,
      JsonObject context,
      String error,
      int retryCount,
      Instant now) {
    TableRecord r = ofRun(job, runId);
    r.setValue("state", Job.READY);
    r.setValue("next_run", utc(nextRun));
    r.setValue("retry_count", retryCount);
    r.setValue("context", context.toString());
    r.setValue("last_error", error);
    clearRun(r, now);
    r.updateMultiple();
  }

  @Override
  public void fail(Job job, String runId, String error, JsonObject context, Instant now) {
    TableRecord r = ofRun(job, runId);
    r.setValue("state", Job.ERROR);
    r.setValue("retry_count", 0);
    r.setValue("context", context.toString());
    r.setValue("last_error", error);
    clearRun(r, now);
    r.updateMultiple();
  }

  @Override
  public void delete(Job job, String runId) {
    ofRun(job, runId).deleteMultiple();
  }

  @Override
  public void park(Job job, String runId, String error, Instant now) {
    TableRecord r = ofRun(job, runId);
    r.setValue("state", Job.READY);
    r.setValue("trigger_type", "on_demand");
    r.setValue("last_error", error);
    clearRun(r, now);
    r.updateMultiple();
  }

  @Override
  public void release(List<Job> jobs, String node, Instant now) {
    for (Job job : jobs) {
      TableRecord r = new TableRecord(database, JOBS);
      r.addQuery("id", job.id);
      r.addQuery("state", Job.QUEUED);
      r.addQuery("owner", node);
      r.setValue("state", Job.READY);
      r.setValue("owner", null);
      r.setValue("updated_at", utc(now));
      r.updateMultiple();
    }
  }

  @Override
  public int recoverStuck(String node, Set<String> known, Instant idleBefore, Instant now) {
    int reset = 0;
    for (int state : new int[] {Job.QUEUED, Job.RUNNING}) {
      TableRecord stuck = new TableRecord(database, JOBS);
      stuck.addQuery("owner", node);
      stuck.addQuery("state", state);
      stuck.addQuery("updated_at", "<", utc(idleBefore));
      stuck.query();
      while (stuck.next()) {
        String id = stuck.getValue("id");
        if (known.contains(id)) {
          continue;
        }
        TableRecord r = new TableRecord(database, JOBS);
        r.addQuery("id", id);
        r.addQuery("state", state);
        r.addQuery("owner", node);
        r.setValue("state", Job.READY);
        clearRun(r, now);
        reset += r.updateMultiple();
      }
    }
    return reset;
  }

  /** A handle that matches one job by id and the run that owns it. */
  private TableRecord ofRun(Job job, String runId) {
    TableRecord r = new TableRecord(database, JOBS);
    r.addQuery("id", job.id);
    r.addQuery("run_id", runId);
    return r;
  }

  /** Ends ownership: no owner, no run id. */
  private static void clearRun(TableRecord r, Instant now) {
    r.setValue("owner", null);
    r.setValue("run_id", null);
    r.setValue("updated_at", utc(now));
  }

  private static OffsetDateTime utc(Instant t) {
    return t == null ? null : t.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(String text) {
    return text == null ? null : OffsetDateTime.parse(text).toInstant();
  }

  private static Job toJob(TableRecord r) {
    Job job = new Job();
    job.id = r.getValue("id");
    job.name = r.getValue("name");
    job.handlerType = r.getValue("handler_type");
    job.handler = r.getValue("handler");
    job.maxRetries = Integer.parseInt(r.getValue("max_retries"));
    job.retryDelaySeconds = Integer.parseInt(r.getValue("retry_delay_seconds"));
    job.retryExponential = Boolean.parseBoolean(r.getValue("retry_exponential"));
    job.retryMaxDelaySeconds = Integer.parseInt(r.getValue("retry_max_delay_seconds"));
    job.retryJitter = Double.parseDouble(r.getValue("retry_jitter"));
    job.retryCount = Integer.parseInt(r.getValue("retry_count"));
    job.triggerType = r.getValue("trigger_type");
    job.triggerSpec = parse(r.getValue("trigger_spec"));
    job.nextRun = instant(r.getValue("next_run"));
    job.state = Integer.parseInt(r.getValue("state"));
    job.priority = Integer.parseInt(r.getValue("priority"));
    job.owner = r.getValue("owner");
    job.runId = r.getValue("run_id");
    job.context = parse(r.getValue("context"));
    job.lastError = r.getValue("last_error");
    job.runEnd = instant(r.getValue("run_end"));
    return job;
  }

  /** A row with unreadable JSON still loads, so the worker can park it with a clear error. */
  private static JsonObject parse(String json) {
    try {
      return JsonParser.parseString(json).getAsJsonObject();
    } catch (RuntimeException e) {
      return new JsonObject();
    }
  }
}
