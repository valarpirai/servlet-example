package com.example.scheduler;

import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

/** {@link JobRepository} on the {@code scheduled_jobs} table. Times are stored in UTC. */
public class SqlJobRepository implements JobRepository {

  private static final Table<Record> JOBS = table("scheduled_jobs");
  private static final Table<Record> RUNS = table("scheduled_job_runs");
  private static final Field<OffsetDateTime> STARTED_AT = field("started_at", OffsetDateTime.class);
  private static final Field<String> ID = field("id", String.class);
  private static final Field<String> NAME = field("name", String.class);
  private static final Field<String> HANDLER = field("handler", String.class);
  private static final Field<String> HANDLER_TYPE = field("handler_type", String.class);
  private static final Field<Integer> MAX_RETRIES = field("max_retries", Integer.class);
  private static final Field<Integer> RETRY_DELAY = field("retry_delay_seconds", Integer.class);
  private static final Field<Boolean> RETRY_EXPONENTIAL = field("retry_exponential", Boolean.class);
  private static final Field<Integer> RETRY_MAX_DELAY =
      field("retry_max_delay_seconds", Integer.class);
  private static final Field<java.math.BigDecimal> RETRY_JITTER =
      field("retry_jitter", java.math.BigDecimal.class);
  private static final Field<Integer> RETRY_COUNT = field("retry_count", Integer.class);
  private static final Field<String> TRIGGER_TYPE = field("trigger_type", String.class);
  private static final Field<String> TRIGGER_SPEC = field("trigger_spec", String.class);
  private static final Field<OffsetDateTime> NEXT_RUN = field("next_run", OffsetDateTime.class);
  private static final Field<Integer> STATE = field("state", Integer.class);
  private static final Field<Integer> PRIORITY = field("priority", Integer.class);
  private static final Field<String> OWNER = field("owner", String.class);
  private static final Field<String> RUN_ID = field("run_id", String.class);
  private static final Field<String> CONTEXT = field("context", String.class);
  private static final Field<String> LAST_ERROR = field("last_error", String.class);
  private static final Field<OffsetDateTime> RUN_END = field("run_end", OffsetDateTime.class);
  private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);

  private final Database database;

  public SqlJobRepository(Database database) {
    this.database = database;
  }

  @Override
  public void recordRun(JobRun run) throws Exception {
    database.query(
        dsl ->
            dsl.insertInto(RUNS)
                .set(field("job_id", String.class), run.jobId)
                .set(field("job_name", String.class), run.jobName)
                .set(field("handler_type", String.class), run.handlerType)
                .set(field("handler", String.class), run.handler)
                .set(field("node", String.class), run.node)
                .set(field("run_id", String.class), run.runId)
                .set(field("status", String.class), run.status)
                .set(field("error", String.class), run.error)
                .set(STARTED_AT, utc(run.startedAt))
                .set(field("ended_at", OffsetDateTime.class), utc(run.endedAt))
                .set(field("duration_ms", Long.class), run.durationMillis)
                .execute());
  }

  @Override
  public int purgeRuns(Instant before) throws Exception {
    return database.query(dsl -> dsl.deleteFrom(RUNS).where(STARTED_AT.lt(utc(before))).execute());
  }

  @Override
  public Map<Integer, Long> countByState() throws Exception {
    return database.query(
        dsl -> {
          Map<Integer, Long> counts = new HashMap<>();
          dsl.select(STATE, count())
              .from(JOBS)
              .groupBy(STATE)
              .fetch()
              .forEach(r -> counts.put(r.value1(), r.value2().longValue()));
          return counts;
        });
  }

  @Override
  public void insert(Job job, Instant now) throws Exception {
    database.query(
        dsl ->
            dsl.insertInto(JOBS)
                .set(ID, job.id)
                .set(NAME, job.name)
                .set(HANDLER_TYPE, job.handlerType)
                .set(HANDLER, job.handler)
                .set(MAX_RETRIES, job.maxRetries)
                .set(RETRY_DELAY, job.retryDelaySeconds)
                .set(RETRY_EXPONENTIAL, job.retryExponential)
                .set(RETRY_MAX_DELAY, job.retryMaxDelaySeconds)
                .set(RETRY_JITTER, java.math.BigDecimal.valueOf(job.retryJitter))
                .set(TRIGGER_TYPE, job.triggerType)
                .set(TRIGGER_SPEC, job.triggerSpec.toString())
                .set(NEXT_RUN, utc(job.nextRun))
                .set(STATE, Job.READY)
                .set(PRIORITY, job.priority)
                .set(CONTEXT, job.context.toString())
                .set(RUN_END, utc(job.runEnd))
                .set(UPDATED_AT, utc(now))
                .execute());
  }

  @Override
  public List<Job> claim(int limit, String node, Instant now) throws Exception {
    return database.query(
        dsl -> {
          OffsetDateTime due = utc(now);
          List<Job> claimed = new ArrayList<>();
          for (Record row :
              dsl.select()
                  .from(JOBS)
                  .where(STATE.eq(Job.READY))
                  .and(TRIGGER_TYPE.ne("on_demand"))
                  .and(NEXT_RUN.isNull().or(NEXT_RUN.le(due)))
                  .and(OWNER.isNull().or(OWNER.eq(node)))
                  .orderBy(PRIORITY.asc(), NEXT_RUN.asc().nullsFirst())
                  .limit(limit)
                  .fetch()) {
            Job job = toJob(row);
            int won =
                dsl.update(JOBS)
                    .set(STATE, Job.QUEUED)
                    .set(OWNER, node)
                    .set(UPDATED_AT, due)
                    .where(ID.eq(job.id))
                    .and(STATE.eq(Job.READY))
                    .execute();
            if (won == 1) {
              job.state = Job.QUEUED;
              job.owner = node;
              claimed.add(job);
            }
          }
          return claimed;
        });
  }

  @Override
  public boolean acquire(Job job, String node, String runId, Instant now) throws Exception {
    return database.query(
            dsl ->
                dsl.update(JOBS)
                    .set(STATE, Job.RUNNING)
                    .set(OWNER, node)
                    .set(RUN_ID, runId)
                    .set(UPDATED_AT, utc(now))
                    .where(ID.eq(job.id))
                    .and(STATE.in(Job.READY, Job.QUEUED))
                    .and(OWNER.isNull().or(OWNER.eq(node)))
                    .execute())
        == 1;
  }

  @Override
  public void reschedule(
      Job job,
      String runId,
      Instant nextRun,
      JsonObject context,
      String error,
      int retryCount,
      Instant now)
      throws Exception {
    database.query(
        dsl ->
            dsl.update(JOBS)
                .set(STATE, Job.READY)
                .set(NEXT_RUN, utc(nextRun))
                .set(RETRY_COUNT, retryCount)
                .set(CONTEXT, context.toString())
                .set(LAST_ERROR, error)
                .set(OWNER, (String) null)
                .set(RUN_ID, (String) null)
                .set(UPDATED_AT, utc(now))
                .where(ID.eq(job.id))
                .and(RUN_ID.eq(runId))
                .execute());
  }

  @Override
  public void fail(Job job, String runId, String error, JsonObject context, Instant now)
      throws Exception {
    database.query(
        dsl ->
            dsl.update(JOBS)
                .set(STATE, Job.ERROR)
                .set(RETRY_COUNT, 0)
                .set(CONTEXT, context.toString())
                .set(LAST_ERROR, error)
                .set(OWNER, (String) null)
                .set(RUN_ID, (String) null)
                .set(UPDATED_AT, utc(now))
                .where(ID.eq(job.id))
                .and(RUN_ID.eq(runId))
                .execute());
  }

  @Override
  public void delete(Job job, String runId) throws Exception {
    database.query(
        dsl -> dsl.deleteFrom(JOBS).where(ID.eq(job.id)).and(RUN_ID.eq(runId)).execute());
  }

  @Override
  public void park(Job job, String runId, String error, Instant now) throws Exception {
    database.query(
        dsl ->
            dsl.update(JOBS)
                .set(STATE, Job.READY)
                .set(TRIGGER_TYPE, "on_demand")
                .set(LAST_ERROR, error)
                .set(OWNER, (String) null)
                .set(RUN_ID, (String) null)
                .set(UPDATED_AT, utc(now))
                .where(ID.eq(job.id))
                .and(RUN_ID.eq(runId))
                .execute());
  }

  @Override
  public void release(List<Job> jobs, String node, Instant now) throws Exception {
    if (jobs.isEmpty()) {
      return;
    }
    List<String> ids = jobs.stream().map(j -> j.id).toList();
    database.query(
        dsl ->
            dsl.update(JOBS)
                .set(STATE, Job.READY)
                .set(OWNER, (String) null)
                .set(UPDATED_AT, utc(now))
                .where(ID.in(ids))
                .and(STATE.eq(Job.QUEUED))
                .and(OWNER.eq(node))
                .execute());
  }

  @Override
  public int recoverStuck(String node, Set<String> known, Instant idleBefore, Instant now)
      throws Exception {
    return database.query(
        dsl -> {
          Condition where =
              OWNER
                  .eq(node)
                  .and(STATE.in(Job.QUEUED, Job.RUNNING))
                  .and(UPDATED_AT.lt(utc(idleBefore)));
          if (!known.isEmpty()) {
            where = where.and(ID.notIn(known));
          }
          return dsl.update(JOBS)
              .set(STATE, Job.READY)
              .set(OWNER, (String) null)
              .set(RUN_ID, (String) null)
              .set(UPDATED_AT, utc(now))
              .where(where)
              .execute();
        });
  }

  private static OffsetDateTime utc(Instant t) {
    return t == null ? null : t.atOffset(ZoneOffset.UTC);
  }

  private static Job toJob(Record r) {
    Job job = new Job();
    job.id = r.get(ID);
    job.name = r.get(NAME);
    job.handlerType = r.get(HANDLER_TYPE);
    job.handler = r.get(HANDLER);
    job.maxRetries = r.get(MAX_RETRIES);
    job.retryDelaySeconds = r.get(RETRY_DELAY);
    job.retryExponential = r.get(RETRY_EXPONENTIAL);
    job.retryMaxDelaySeconds = r.get(RETRY_MAX_DELAY);
    job.retryJitter = r.get(RETRY_JITTER).doubleValue();
    job.retryCount = r.get(RETRY_COUNT);
    job.triggerType = r.get(TRIGGER_TYPE);
    job.triggerSpec = parse(r.get(TRIGGER_SPEC));
    OffsetDateTime next = r.get(NEXT_RUN);
    job.nextRun = next == null ? null : next.toInstant();
    job.state = r.get(STATE);
    job.priority = r.get(PRIORITY);
    job.owner = r.get(OWNER);
    job.runId = r.get(RUN_ID);
    job.context = parse(r.get(CONTEXT));
    job.lastError = r.get(LAST_ERROR);
    OffsetDateTime end = r.get(RUN_END);
    job.runEnd = end == null ? null : end.toInstant();
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
