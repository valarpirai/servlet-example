package com.example.scheduler;

import com.example.util.PropertiesUtil;

/** Scheduler settings. Times are in milliseconds. */
public class SchedulerConfig {

  public int workers = 4;
  public int maxInFlight = 100;
  public int claimBatch = 20;
  public long pollMillis = 1000;
  public long sweepMillis = 5 * 60_000L;
  public long stopMillis = 30_000L;

  /** Stuck rows must sit idle this long before a sweep resets them. Covers the claim race. */
  public long sweepGraceMillis = 60_000L;

  /** How long run history is kept. */
  public long historyMillis = 30 * 24 * 3_600_000L;

  /** How far a job with no handler is pushed out. */
  public long missingHandlerDelayMillis = 60 * 60_000L;

  /** Reads the {@code scheduler.*} rows of {@code app_properties}. */
  public static SchedulerConfig fromProperties() {
    SchedulerConfig c = new SchedulerConfig();
    c.workers = PropertiesUtil.getInt("scheduler.workers", c.workers);
    c.maxInFlight = PropertiesUtil.getInt("scheduler.maxInFlight", c.maxInFlight);
    c.claimBatch = PropertiesUtil.getInt("scheduler.claimBatch", c.claimBatch);
    c.pollMillis = PropertiesUtil.getInt("scheduler.pollSeconds", 1) * 1000L;
    c.sweepMillis = PropertiesUtil.getInt("scheduler.sweepMinutes", 5) * 60_000L;
    c.stopMillis = PropertiesUtil.getInt("scheduler.stopSeconds", 30) * 1000L;
    c.historyMillis = PropertiesUtil.getInt("scheduler.historyDays", 30) * 24 * 3_600_000L;
    return c;
  }
}
