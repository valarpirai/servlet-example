package com.example.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchedulerHistoryTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

  private static SchedulerConfig config() {
    SchedulerConfig c = new SchedulerConfig();
    c.workers = 1;
    c.pollMillis = 20;
    c.stopMillis = 2000;
    return c;
  }

  private static JsonObject seconds(int n) {
    JsonObject o = new JsonObject();
    o.addProperty("seconds", n);
    return o;
  }

  private static void waitUntil(java.util.function.BooleanSupplier ok) throws Exception {
    long end = System.currentTimeMillis() + 5000;
    while (!ok.getAsBoolean()) {
      if (System.currentTimeMillis() > end) {
        throw new AssertionError("condition not met in 5s");
      }
      Thread.sleep(10);
    }
  }

  private static int count(FakeJobRepository repo) {
    synchronized (repo) {
      return repo.runs.size();
    }
  }

  @Test
  void aSuccessfulOnceJobLeavesOnlyAHistoryRow() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "once", new JsonObject());
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register("h", ctx -> ctx);
    Scheduler s = new Scheduler(repo, handlers, CLOCK, "n1", config());
    s.start();
    waitUntil(() -> count(repo) == 1);
    s.stop();
    JobRun run = repo.runs.get(0);
    assertEquals("a", run.jobId);
    assertEquals("h", run.handler);
    assertEquals("n1", run.node);
    assertEquals(JobRun.SUCCESS, run.status);
    assertNotNull(run.runId);
    assertEquals(CLOCK.instant(), run.startedAt);
    assertTrue(repo.rows.isEmpty());
  }

  @Test
  void failureNoHandlerAndParkAreEachRecorded() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("fails", "boom", "once", new JsonObject());
    repo.add("lost", "nope", "interval", seconds(60));
    repo.add("bad", "boom", "interval", new JsonObject());
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register(
        "boom",
        ctx -> {
          throw new IllegalStateException("boom");
        });
    Scheduler s = new Scheduler(repo, handlers, CLOCK, "n1", config());
    s.start();
    waitUntil(() -> count(repo) == 3);
    s.stop();
    Map<String, String> byJob = new java.util.HashMap<>();
    repo.runs.forEach(r -> byJob.put(r.jobId, r.status));
    assertEquals(JobRun.FAILED, byJob.get("fails"));
    assertEquals(JobRun.NO_HANDLER, byJob.get("lost"));
    assertEquals(JobRun.PARKED, byJob.get("bad"));
  }

  @Test
  void purgeRemovesRunsOlderThanTheCutoff() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    JobRun old = new JobRun();
    old.startedAt = CLOCK.instant().minusSeconds(100);
    JobRun recent = new JobRun();
    recent.startedAt = CLOCK.instant();
    repo.recordRun(old);
    repo.recordRun(recent);
    assertEquals(1, repo.purgeRuns(CLOCK.instant().minusSeconds(50)));
    assertEquals(1, repo.runs.size());
  }

  @Test
  void statsReportCountersLatenessAndHandlerTimes() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    Job job = repo.add("a", "h", "once", new JsonObject());
    repo.row("a").nextRun = CLOCK.instant().minusSeconds(5);
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register("h", ctx -> ctx);
    Scheduler s = new Scheduler(repo, handlers, CLOCK, "n1", config());
    s.start();
    waitUntil(() -> count(repo) == 1);
    s.stop();
    Map<String, Object> stats = s.stats();
    assertEquals("n1", stats.get("node"));
    assertEquals(1L, stats.get("ran"));
    assertEquals(1L, stats.get("claimed"));
    assertEquals(5000L, stats.get("latenessMaxMillis"));
    @SuppressWarnings("unchecked")
    Map<String, Object> h =
        (Map<String, Object>) ((Map<String, Object>) stats.get("handlers")).get("java:h");
    assertEquals(1L, h.get("runs"));
    assertEquals(0L, h.get("failures"));
    assertEquals(0, s.tableCounts().get("running"));
  }
}
