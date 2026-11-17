package com.example.scheduler;

import java.time.Instant;
import java.util.Optional;

/** Computes when a job runs next. One implementation per trigger type. */
public interface Recurrence {

  /**
   * Computes the next due time after a run.
   *
   * @param job the job that just ran
   * @param now the current time
   * @return the next due time, or empty when the job does not repeat
   */
  Optional<Instant> next(Job job, Instant now);
}
