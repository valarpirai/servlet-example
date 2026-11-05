package com.example.scheduler;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Every read and write of {@code scheduled_jobs}. Each state change carries a filter, so a stale
 * writer changes nothing.
 */
public interface JobRepository {

  /**
   * Claims up to {@code limit} due jobs for this node: ready, due, lowest priority value first.
   * Each job moves to queued only if no other node got there first.
   *
   * @param limit most jobs to return
   * @param node id of the claiming node
   * @param now the current time
   * @return the jobs this node now owns, in claim order
   */
  List<Job> claim(int limit, String node, Instant now) throws Exception;

  /**
   * Moves a claimed job to running.
   *
   * @param job the job, owned by {@code node}
   * @param node id of this node
   * @param runId id for this run
   * @param now the current time
   * @return false when another node owns the job, so the caller must drop it
   */
  boolean acquire(Job job, String node, String runId, Instant now) throws Exception;

  /**
   * Ends a run: back to ready with the next due time, in one write.
   *
   * @param job the job that ran
   * @param runId run id from {@link #acquire}
   * @param nextRun next due time
   * @param context state to keep for the next run
   * @param error failure text, or null on success
   * @param now the current time
   */
  void reschedule(
      Job job, String runId, Instant nextRun, JsonObject context, String error, Instant now)
      throws Exception;

  /** Ends a run that failed and does not repeat. The job stays in the table in error state. */
  void fail(Job job, String runId, String error, JsonObject context, Instant now) throws Exception;

  /** Ends a run that does not repeat and succeeded. Deletes the row. */
  void delete(Job job, String runId) throws Exception;

  /**
   * Sets a job aside so it is never claimed again: trigger becomes {@code on_demand}.
   *
   * @param error why the job was parked
   */
  void park(Job job, String runId, String error, Instant now) throws Exception;

  /** Hands queued jobs back, for shutdown. Touches only rows still queued by {@code node}. */
  void release(List<Job> jobs, String node, Instant now) throws Exception;

  /**
   * Resets rows that this node owns, in queued or running state, but holds in no memory.
   *
   * @param node id of this node
   * @param known ids of jobs this node holds in memory
   * @param idleBefore only rows not updated since this time are reset
   * @param now the current time
   * @return rows reset
   */
  int recoverStuck(String node, Set<String> known, Instant idleBefore, Instant now)
      throws Exception;
}
