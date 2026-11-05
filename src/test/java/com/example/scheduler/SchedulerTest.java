package com.example.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SchedulerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

  private static JsonObject seconds(int n) {
    JsonObject o = new JsonObject();
    o.addProperty("seconds", n);
    return o;
  }

  private static SchedulerConfig config() {
    SchedulerConfig c = new SchedulerConfig();
    c.workers = 2;
    c.pollMillis = 20;
    c.stopMillis = 2000;
    return c;
  }

  private static Scheduler scheduler(
      FakeJobRepository repo, HandlerRegistry handlers, String node, SchedulerConfig c) {
    return new Scheduler(repo, handlers, CLOCK, node, c);
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

  @Test
  void intervalJobRunsAndIsRescheduled() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "interval", seconds(60));
    AtomicInteger runs = new AtomicInteger();
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register(
        "h",
        ctx -> {
          runs.incrementAndGet();
          return ctx;
        });
    Scheduler s = scheduler(repo, handlers, "n1", config());
    s.start();
    waitUntil(() -> runs.get() == 1 && repo.row("a").state == Job.READY);
    s.stop();
    assertEquals(CLOCK.instant().plusSeconds(60), repo.row("a").nextRun);
    assertNull(repo.row("a").owner);
    assertNull(repo.row("a").lastError);
  }

  @Test
  void onceJobIsDeletedAfterASuccessfulRun() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "once", new JsonObject());
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register("h", ctx -> ctx);
    Scheduler s = scheduler(repo, handlers, "n1", config());
    s.start();
    waitUntil(() -> repo.row("a") == null);
    s.stop();
  }

  @Test
  void failedOnceJobStaysInErrorState() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "once", new JsonObject());
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register(
        "h",
        ctx -> {
          throw new IllegalStateException("boom");
        });
    Scheduler s = scheduler(repo, handlers, "n1", config());
    s.start();
    waitUntil(() -> repo.row("a").state == Job.ERROR);
    s.stop();
    assertTrue(repo.row("a").lastError.contains("boom"));
  }

  @Test
  void failedIntervalJobKeepsRepeatingAndRecordsTheError() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "interval", seconds(60));
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register(
        "h",
        ctx -> {
          throw new IllegalStateException("boom");
        });
    Scheduler s = scheduler(repo, handlers, "n1", config());
    s.start();
    waitUntil(() -> repo.row("a").lastError != null);
    s.stop();
    assertEquals(Job.READY, repo.row("a").state);
    assertEquals(CLOCK.instant().plusSeconds(60), repo.row("a").nextRun);
  }

  @Test
  void missingHandlerPushesTheJobOutAnHour() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "nope", "interval", seconds(60));
    Scheduler s = scheduler(repo, new HandlerRegistry(n -> null), "n1", config());
    s.start();
    waitUntil(() -> repo.row("a").lastError != null);
    s.stop();
    assertEquals(Job.READY, repo.row("a").state);
    assertEquals(CLOCK.instant().plusSeconds(3600), repo.row("a").nextRun);
    assertTrue(repo.row("a").lastError.contains("no handler"));
  }

  @Test
  void badTriggerSpecParksTheJob() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "interval", new JsonObject());
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register("h", ctx -> ctx);
    Scheduler s = scheduler(repo, handlers, "n1", config());
    s.start();
    waitUntil(() -> "on_demand".equals(repo.row("a").triggerType));
    s.stop();
    assertTrue(repo.row("a").lastError.contains("bad trigger_spec"));
  }

  @Test
  void twoSchedulersOnOneTableRunEachJobOnce() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    int jobs = 50;
    for (int i = 0; i < jobs; i++) {
      repo.add("j" + i, "h", "once", new JsonObject());
    }
    AtomicInteger runs = new AtomicInteger();
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register(
        "h",
        ctx -> {
          runs.incrementAndGet();
          return ctx;
        });
    Scheduler a = scheduler(repo, handlers, "n1", config());
    Scheduler b = scheduler(repo, handlers, "n2", config());
    a.start();
    b.start();
    waitUntil(() -> repo.rows.isEmpty());
    a.stop();
    b.stop();
    assertEquals(jobs, runs.get());
  }

  @Test
  void sweepResetsRowsHeldInNoMemory() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "interval", seconds(60));
    repo.row("a").state = Job.RUNNING;
    repo.row("a").owner = "n1";
    Scheduler s = scheduler(repo, new HandlerRegistry(n -> null), "n1", config());
    s.sweep();
    assertEquals(Job.READY, repo.row("a").state);
    assertNull(repo.row("a").owner);
    assertEquals(1L, s.stats().get("recovered"));
  }

  @Test
  void startRecoversRowsALostRunLeftBehind() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    repo.add("a", "h", "interval", seconds(3600));
    repo.row("a").state = Job.RUNNING;
    repo.row("a").owner = "n1";
    repo.row("a").nextRun = CLOCK.instant().plusSeconds(3600);
    Scheduler s = scheduler(repo, new HandlerRegistry(n -> null), "n1", config());
    s.start();
    s.stop();
    assertEquals(Job.READY, repo.row("a").state);
  }

  @Test
  void shutdownHandsQueuedJobsBack() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    for (int i = 0; i < 5; i++) {
      repo.add("j" + i, "h", "interval", seconds(60));
    }
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(1);
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register(
        "h",
        ctx -> {
          started.countDown();
          release.await(10, TimeUnit.SECONDS);
          return ctx;
        });
    SchedulerConfig c = config();
    c.workers = 1;
    Scheduler s = scheduler(repo, handlers, "n1", c);
    s.start();
    started.await(5, TimeUnit.SECONDS);
    waitUntil(() -> (int) s.stats().get("queued") == 4);
    Thread stopper = new Thread(s::stop);
    stopper.start();
    waitUntil(() -> repo.rows.values().stream().filter(j -> j.state == Job.READY).count() == 4);
    release.countDown();
    stopper.join(5000);
    assertTrue(repo.rows.values().stream().noneMatch(j -> j.state == Job.QUEUED));
  }

  @Test
  void inFlightNeverExceedsTheCap() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    for (int i = 0; i < 20; i++) {
      repo.add("j" + i, "h", "interval", seconds(60));
    }
    CountDownLatch release = new CountDownLatch(1);
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register(
        "h",
        ctx -> {
          release.await(10, TimeUnit.SECONDS);
          return ctx;
        });
    SchedulerConfig c = config();
    c.workers = 1;
    c.maxInFlight = 3;
    Scheduler s = scheduler(repo, handlers, "n1", c);
    s.tick();
    s.tick();
    assertEquals(3, s.stats().get("held"));
    assertEquals(3, repo.rows.values().stream().filter(j -> j.state == Job.QUEUED).count());
    release.countDown();
  }
}
