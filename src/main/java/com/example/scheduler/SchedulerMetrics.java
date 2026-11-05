package com.example.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Counters for one scheduler since it started. Every method is safe to call from any thread. */
public class SchedulerMetrics {

  private final AtomicLong ran = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();
  private final AtomicLong retried = new AtomicLong();
  private final AtomicLong lostRaces = new AtomicLong();
  private final AtomicLong recovered = new AtomicLong();
  private final AtomicLong claimed = new AtomicLong();
  private final AtomicLong lastClaimMillis = new AtomicLong();
  private final AtomicLong latenessCount = new AtomicLong();
  private final AtomicLong latenessTotalMillis = new AtomicLong();
  private final AtomicLong latenessMaxMillis = new AtomicLong();
  private final Map<String, HandlerTimes> handlers = new ConcurrentHashMap<>();

  /** Run time of one handler key. */
  private static final class HandlerTimes {
    final AtomicLong runs = new AtomicLong();
    final AtomicLong failures = new AtomicLong();
    final AtomicLong totalMillis = new AtomicLong();
    final AtomicLong maxMillis = new AtomicLong();
  }

  /** A claim query returned {@code count} jobs and took {@code millis}. */
  void claim(int count, long millis) {
    claimed.addAndGet(count);
    lastClaimMillis.set(millis);
  }

  /** Another node won the optimistic update for a job this node had queued. */
  void lostRace() {
    lostRaces.incrementAndGet();
  }

  /** A failed job was put back for another attempt. */
  void retried() {
    retried.incrementAndGet();
  }

  /** The sweep or startup recovery reset {@code count} rows. */
  void recovered(int count) {
    recovered.addAndGet(count);
  }

  /** A job began running. Lateness is the wait past its due time. Jobs with no due time skip it. */
  void started(Job job, Instant now) {
    if (job.nextRun == null) {
      return;
    }
    long late = Math.max(0, Duration.between(job.nextRun, now).toMillis());
    latenessCount.incrementAndGet();
    latenessTotalMillis.addAndGet(late);
    latenessMaxMillis.accumulateAndGet(late, Math::max);
  }

  /** A handler returned or threw. */
  void finished(String handler, long millis, boolean ok) {
    (ok ? ran : failed).incrementAndGet();
    HandlerTimes t = handlers.computeIfAbsent(handler, k -> new HandlerTimes());
    t.runs.incrementAndGet();
    if (!ok) {
      t.failures.incrementAndGet();
    }
    t.totalMillis.addAndGet(millis);
    t.maxMillis.accumulateAndGet(millis, Math::max);
  }

  /** A point-in-time copy, ready for JSON. */
  public Map<String, Object> snapshot() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ran", ran.get());
    out.put("failed", failed.get());
    out.put("retried", retried.get());
    out.put("lostRaces", lostRaces.get());
    out.put("recovered", recovered.get());
    out.put("claimed", claimed.get());
    out.put("lastClaimMillis", lastClaimMillis.get());
    long n = latenessCount.get();
    out.put("latenessAvgMillis", n == 0 ? 0 : latenessTotalMillis.get() / n);
    out.put("latenessMaxMillis", latenessMaxMillis.get());
    Map<String, Object> byHandler = new LinkedHashMap<>();
    handlers.forEach(
        (key, t) -> {
          Map<String, Object> one = new LinkedHashMap<>();
          long runs = t.runs.get();
          one.put("runs", runs);
          one.put("failures", t.failures.get());
          one.put("avgMillis", runs == 0 ? 0 : t.totalMillis.get() / runs);
          one.put("maxMillis", t.maxMillis.get());
          byHandler.put(key, one);
        });
    out.put("handlers", byHandler);
    return out;
  }
}
