package com.example.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RecurrencesTest {

  private static Job job(String type, String key, Object value) {
    Job job = new Job();
    job.triggerType = type;
    if (value instanceof Number n) {
      job.triggerSpec.addProperty(key, n);
    } else if (value != null) {
      job.triggerSpec.addProperty(key, value.toString());
    }
    return job;
  }

  @Test
  void onceNeverRepeats() {
    assertTrue(Recurrences.forType("once").next(job("once", null, null), Instant.now()).isEmpty());
  }

  @Test
  void intervalAddsToThePreviousDueTime() {
    Job j = job("interval", "seconds", 60);
    j.nextRun = Instant.parse("2026-01-01T10:00:00Z");
    Instant now = Instant.parse("2026-01-01T10:00:05Z");
    assertEquals(
        Instant.parse("2026-01-01T10:01:00Z"), Recurrences.forType("interval").next(j, now).get());
  }

  @Test
  void intervalThatFellBehindSkipsToNowPlusInterval() {
    Job j = job("interval", "seconds", 60);
    j.nextRun = Instant.parse("2026-01-01T10:00:00Z");
    Instant now = Instant.parse("2026-01-01T12:00:00Z");
    assertEquals(
        Instant.parse("2026-01-01T12:01:00Z"), Recurrences.forType("interval").next(j, now).get());
  }

  @Test
  void intervalWithNullDueTimeCountsFromNow() {
    Job j = job("interval", "seconds", 30);
    Instant now = Instant.parse("2026-01-01T10:00:00Z");
    assertEquals(
        Instant.parse("2026-01-01T10:00:30Z"), Recurrences.forType("interval").next(j, now).get());
  }

  @Test
  void dailyPicksTodayWhenTheTimeIsAhead() {
    Job j = job("daily", "time", "02:30");
    Instant now = Instant.parse("2026-01-01T01:00:00Z");
    assertEquals(
        Instant.parse("2026-01-01T02:30:00Z"), Recurrences.forType("daily").next(j, now).get());
  }

  @Test
  void dailyPicksTomorrowWhenTheTimeHasPassed() {
    Job j = job("daily", "time", "02:30");
    Instant now = Instant.parse("2026-01-01T02:30:00Z");
    assertEquals(
        Instant.parse("2026-01-02T02:30:00Z"), Recurrences.forType("daily").next(j, now).get());
  }

  @Test
  void onDemandHasNoRecurrence() {
    assertNull(Recurrences.forType("on_demand"));
  }

  @Test
  void validateRejectsUnknownTypeAndBadSpec() {
    assertThrows(
        IllegalArgumentException.class, () -> Recurrences.validate(job("weekly", null, null)));
    assertThrows(
        IllegalArgumentException.class, () -> Recurrences.validate(job("interval", "seconds", 0)));
    Job noSpec = new Job();
    noSpec.triggerType = "interval";
    noSpec.triggerSpec = new JsonObject();
    assertThrows(IllegalArgumentException.class, () -> Recurrences.validate(noSpec));
    Recurrences.validate(job("daily", "time", "02:30"));
  }
}
