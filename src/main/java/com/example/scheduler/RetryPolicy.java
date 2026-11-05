package com.example.scheduler;

/** How long a failed job waits before its next attempt. Reads the retry fields of the job. */
public final class RetryPolicy {

  private RetryPolicy() {}

  /**
   * Computes the wait before a retry.
   *
   * <p>Fixed: the base delay. Exponential: the base delay doubled for each earlier retry. Jitter
   * then scales the wait by a random factor between {@code 1 - jitter} and {@code 1 + jitter}. The
   * result never exceeds the job's maximum delay.
   *
   * @param job the job that failed
   * @param attempt which retry this is, starting at 1
   * @param random a value from 0 up to 1; pass a random number in production, a fixed one in tests
   * @return the wait in milliseconds, at least 1
   */
  public static long delayMillis(Job job, int attempt, double random) {
    double seconds = job.retryDelaySeconds;
    if (job.retryExponential) {
      seconds *= Math.pow(2, Math.min(attempt - 1, 30));
    }
    seconds *= 1 + job.retryJitter * (2 * random - 1);
    seconds = Math.min(seconds, job.retryMaxDelaySeconds);
    return Math.max(1, Math.round(seconds * 1000));
  }
}
