package com.example.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SchedulerRetryTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

  private static SchedulerConfig config() {
    SchedulerConfig c = new SchedulerConfig();
    c.workers = 1;
    c.pollMillis = 20;
    c.stopMillis = 2000;
    return c;
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

  private static Scheduler scheduler(FakeJobRepository repo, HandlerRegistry handlers) {
    Scheduler s = new Scheduler(repo, handlers, CLOCK, "n1", config());
    s.setRandom(() -> 0.5); // no jitter shift
    return s;
  }

  private static HandlerRegistry failing(AtomicInteger calls) {
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register(
        "h",
        ctx -> {
          calls.incrementAndGet();
          throw new IllegalStateException("boom");
        });
    return handlers;
  }

  @Test
  void aFailedJobWaitsOutTheBackoffThenIsRetried() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    Job j = repo.add("a", "h", "once", new JsonObject());
    repo.row("a").maxRetries = 3;
    repo.row("a").retryDelaySeconds = 10;
    AtomicInteger calls = new AtomicInteger();
    Scheduler s = scheduler(repo, failing(calls));
    s.start();
    waitUntil(() -> repo.row("a").retryCount == 1);
    s.stop();
    assertEquals(Job.READY, repo.row("a").state);
    assertEquals(CLOCK.instant().plusSeconds(10), repo.row("a").nextRun);
    assertTrue(repo.row("a").lastError.contains("boom"));
    assertEquals(1L, s.stats().get("retried"));
  }

  @Test
  void secondRetryWaitsTwiceAsLongWhenExponential() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "once", new JsonObject());
    repo.row("a").maxRetries = 3;
    repo.row("a").retryDelaySeconds = 10;
    repo.row("a").retryCount = 1;
    Scheduler s = scheduler(repo, failing(new AtomicInteger()));
    s.start();
    waitUntil(() -> repo.row("a").retryCount == 2);
    s.stop();
    assertEquals(CLOCK.instant().plusSeconds(20), repo.row("a").nextRun);
  }

  @Test
  void afterTheLastRetryAOnceJobEndsInErrorWithTheCountReset() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "once", new JsonObject());
    repo.row("a").maxRetries = 2;
    repo.row("a").retryCount = 2;
    Scheduler s = scheduler(repo, failing(new AtomicInteger()));
    s.start();
    waitUntil(() -> repo.row("a").state == Job.ERROR);
    s.stop();
    assertEquals(0, repo.row("a").retryCount);
    assertEquals(0L, s.stats().get("retried"));
  }

  @Test
  void aRetrySucceedsAndTheOnceJobIsDeleted() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "once", new JsonObject());
    repo.row("a").maxRetries = 3;
    repo.row("a").retryCount = 1;
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register("h", ctx -> ctx);
    Scheduler s = scheduler(repo, handlers);
    s.start();
    waitUntil(() -> repo.row("a") == null);
    s.stop();
  }

  @Test
  void aRepeatingJobThatRecoversStartsCountingFromZeroAgain() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    JsonObject spec = new JsonObject();
    spec.addProperty("seconds", 60);
    repo.add("a", "h", "interval", spec);
    repo.row("a").maxRetries = 3;
    repo.row("a").retryCount = 2;
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register("h", ctx -> ctx);
    Scheduler s = scheduler(repo, handlers);
    s.start();
    waitUntil(() -> repo.row("a").state == Job.READY && repo.row("a").nextRun != null);
    s.stop();
    assertEquals(0, repo.row("a").retryCount);
    assertNull(repo.row("a").lastError);
  }

  @Test
  void aRetriedAttemptStillLeavesAHistoryRow() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "once", new JsonObject());
    repo.row("a").maxRetries = 3;
    repo.row("a").retryDelaySeconds = 10;
    Scheduler s = scheduler(repo, failing(new AtomicInteger()));
    s.start();
    waitUntil(() -> repo.row("a").retryCount == 1);
    waitUntil(
        () -> {
          synchronized (repo) {
            return repo.runs.size() == 1;
          }
        });
    s.stop();
    assertEquals(JobRun.FAILED, repo.runs.get(0).status);
    assertEquals(Job.JAVA, repo.runs.get(0).handlerType);
  }

  @Test
  void scriptTypeUsesTheScriptFactoryNotTheJavaRegistry() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "report", "once", new JsonObject());
    repo.row("a").handlerType = Job.SCRIPT;
    AtomicInteger ran = new AtomicInteger();
    HandlerRegistry handlers =
        new HandlerRegistry(
            name -> {
              assertEquals("report", name);
              return ctx -> {
                ran.incrementAndGet();
                return ctx;
              };
            });
    Scheduler s = scheduler(repo, handlers);
    s.start();
    waitUntil(() -> repo.row("a") == null);
    s.stop();
    assertEquals(1, ran.get());
  }
}
