package com.example.scheduler;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory {@link JobRepository}. Every method is synchronized, like one atomic SQL update. */
public class FakeJobRepository implements JobRepository {

  public final Map<String, Job> rows = new LinkedHashMap<>();

  public synchronized Job add(String id, String handler, String triggerType, JsonObject spec) {
    Job job = new Job();
    job.id = id;
    job.name = id;
    job.handler = handler;
    job.triggerType = triggerType;
    job.triggerSpec = spec;
    rows.put(id, copy(job));
    return job;
  }

  public synchronized Job row(String id) {
    return rows.get(id);
  }

  public final List<JobRun> runs = new ArrayList<>();

  @Override
  public synchronized void recordRun(JobRun run) {
    runs.add(run);
  }

  @Override
  public synchronized int purgeRuns(Instant before) {
    int n = (int) runs.stream().filter(r -> r.startedAt.isBefore(before)).count();
    runs.removeIf(r -> r.startedAt.isBefore(before));
    return n;
  }

  @Override
  public synchronized Map<Integer, Long> countByState() {
    Map<Integer, Long> counts = new java.util.HashMap<>();
    rows.values().forEach(j -> counts.merge(j.state, 1L, Long::sum));
    return counts;
  }

  @Override
  public synchronized void insert(Job job, Instant now) {
    rows.put(job.id, copy(job));
  }

  @Override
  public synchronized List<Job> claim(int limit, String node, Instant now) {
    List<Job> out = new ArrayList<>();
    rows.values().stream()
        .filter(j -> j.state == Job.READY && !"on_demand".equals(j.triggerType))
        .filter(j -> j.nextRun == null || !j.nextRun.isAfter(now))
        .filter(j -> j.owner == null || j.owner.equals(node))
        .sorted(Comparator.comparingInt((Job j) -> j.priority))
        .limit(limit)
        .forEach(
            j -> {
              j.state = Job.QUEUED;
              j.owner = node;
              out.add(copy(j));
            });
    return out;
  }

  @Override
  public synchronized boolean acquire(Job job, String node, String runId, Instant now) {
    Job row = rows.get(job.id);
    if (row == null
        || (row.state != Job.READY && row.state != Job.QUEUED)
        || (row.owner != null && !row.owner.equals(node))) {
      return false;
    }
    row.state = Job.RUNNING;
    row.owner = node;
    row.runId = runId;
    return true;
  }

  @Override
  public synchronized void reschedule(
      Job job,
      String runId,
      Instant nextRun,
      JsonObject context,
      String error,
      int retryCount,
      Instant now) {
    Job row = rows.get(job.id);
    if (row != null && runId.equals(row.runId)) {
      row.state = Job.READY;
      row.retryCount = retryCount;
      row.nextRun = nextRun;
      row.context = context;
      row.lastError = error;
      row.owner = null;
      row.runId = null;
    }
  }

  @Override
  public synchronized void fail(
      Job job, String runId, String error, JsonObject context, Instant now) {
    Job row = rows.get(job.id);
    if (row != null && runId.equals(row.runId)) {
      row.state = Job.ERROR;
      row.retryCount = 0;
      row.lastError = error;
      row.context = context;
      row.owner = null;
      row.runId = null;
    }
  }

  @Override
  public synchronized void delete(Job job, String runId) {
    Job row = rows.get(job.id);
    if (row != null && runId.equals(row.runId)) {
      rows.remove(job.id);
    }
  }

  @Override
  public synchronized void park(Job job, String runId, String error, Instant now) {
    Job row = rows.get(job.id);
    if (row != null && runId.equals(row.runId)) {
      row.state = Job.READY;
      row.triggerType = "on_demand";
      row.lastError = error;
      row.owner = null;
      row.runId = null;
    }
  }

  @Override
  public synchronized void release(List<Job> jobs, String node, Instant now) {
    for (Job job : jobs) {
      Job row = rows.get(job.id);
      if (row != null && row.state == Job.QUEUED && node.equals(row.owner)) {
        row.state = Job.READY;
        row.owner = null;
      }
    }
  }

  @Override
  public synchronized int recoverStuck(
      String node, Set<String> known, Instant idleBefore, Instant now) {
    int n = 0;
    for (Job row : rows.values()) {
      if (node.equals(row.owner)
          && (row.state == Job.QUEUED || row.state == Job.RUNNING)
          && !known.contains(row.id)) {
        row.state = Job.READY;
        row.owner = null;
        row.runId = null;
        n++;
      }
    }
    return n;
  }

  private static Job copy(Job j) {
    Job c = new Job();
    c.id = j.id;
    c.name = j.name;
    c.handlerType = j.handlerType;
    c.handler = j.handler;
    c.maxRetries = j.maxRetries;
    c.retryDelaySeconds = j.retryDelaySeconds;
    c.retryExponential = j.retryExponential;
    c.retryMaxDelaySeconds = j.retryMaxDelaySeconds;
    c.retryJitter = j.retryJitter;
    c.retryCount = j.retryCount;
    c.triggerType = j.triggerType;
    c.triggerSpec = j.triggerSpec;
    c.nextRun = j.nextRun;
    c.state = j.state;
    c.priority = j.priority;
    c.owner = j.owner;
    c.runId = j.runId;
    c.context = j.context;
    c.lastError = j.lastError;
    c.runEnd = j.runEnd;
    return c;
  }
}
