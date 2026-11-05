package com.example.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  private static Job job(boolean exponential, int delay, int max, double jitter) {
    Job j = new Job();
    j.retryExponential = exponential;
    j.retryDelaySeconds = delay;
    j.retryMaxDelaySeconds = max;
    j.retryJitter = jitter;
    return j;
  }

  @Test
  void fixedDelayStaysTheSame() {
    Job j = job(false, 30, 3600, 0);
    assertEquals(30_000, RetryPolicy.delayMillis(j, 1, 0.5));
    assertEquals(30_000, RetryPolicy.delayMillis(j, 5, 0.5));
  }

  @Test
  void exponentialDelayDoublesEachAttempt() {
    Job j = job(true, 10, 3600, 0);
    assertEquals(10_000, RetryPolicy.delayMillis(j, 1, 0.5));
    assertEquals(20_000, RetryPolicy.delayMillis(j, 2, 0.5));
    assertEquals(40_000, RetryPolicy.delayMillis(j, 3, 0.5));
  }

  @Test
  void delayNeverExceedsTheMaximum() {
    Job j = job(true, 10, 100, 0);
    assertEquals(100_000, RetryPolicy.delayMillis(j, 10, 0.5));
    assertEquals(100_000, RetryPolicy.delayMillis(j, 500, 0.5));
  }

  @Test
  void jitterSpreadsTheDelayAroundTheBase() {
    Job j = job(false, 100, 3600, 0.2);
    assertEquals(80_000, RetryPolicy.delayMillis(j, 1, 0.0));
    assertEquals(100_000, RetryPolicy.delayMillis(j, 1, 0.5));
    assertEquals(120_000, RetryPolicy.delayMillis(j, 1, 1.0));
  }

  @Test
  void jitterCannotPushPastTheMaximum() {
    Job j = job(false, 100, 110, 0.2);
    assertEquals(110_000, RetryPolicy.delayMillis(j, 1, 1.0));
  }
}
