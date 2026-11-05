package com.example.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

/** The built-in recurrences, looked up by the {@code trigger_type} column. */
public final class Recurrences {

  private static final Recurrence ONCE = (job, now) -> Optional.empty();

  /** Previous due time plus the interval. A job that fell behind skips to now plus the interval. */
  private static final Recurrence INTERVAL =
      (job, now) -> {
        long seconds = job.triggerSpec.get("seconds").getAsLong();
        if (seconds <= 0) {
          throw new IllegalArgumentException("interval seconds must be positive");
        }
        Instant due = job.nextRun != null ? job.nextRun : now;
        Instant next = due.plus(Duration.ofSeconds(seconds));
        return Optional.of(next.isAfter(now) ? next : now.plusSeconds(seconds));
      };

  /** The next occurrence of {@code time} (HH:mm, UTC) strictly after now. */
  private static final Recurrence DAILY =
      (job, now) -> {
        LocalTime time = LocalTime.parse(job.triggerSpec.get("time").getAsString());
        ZonedDateTime utc = now.atZone(ZoneOffset.UTC);
        ZonedDateTime next = utc.with(time);
        if (!next.isAfter(utc)) {
          next = next.plusDays(1);
        }
        return Optional.of(next.toInstant());
      };

  private static final Map<String, Recurrence> BY_TYPE =
      Map.of("once", ONCE, "interval", INTERVAL, "daily", DAILY);

  private Recurrences() {}

  /**
   * Finds the recurrence for a trigger type.
   *
   * @param triggerType value of the {@code trigger_type} column
   * @return the recurrence, or null for {@code on_demand} and unknown types
   */
  public static Recurrence forType(String triggerType) {
    return BY_TYPE.get(triggerType);
  }

  /**
   * Checks a job's trigger before it is claimed or rescheduled.
   *
   * @param job the job to check
   * @throws IllegalArgumentException when the trigger type or spec is unusable
   */
  public static void validate(Job job) {
    Recurrence recurrence = forType(job.triggerType);
    if (recurrence == null) {
      throw new IllegalArgumentException("unknown trigger type: " + job.triggerType);
    }
    try {
      recurrence.next(job, Instant.EPOCH);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("bad trigger_spec: " + e.getMessage(), e);
    }
  }
}
