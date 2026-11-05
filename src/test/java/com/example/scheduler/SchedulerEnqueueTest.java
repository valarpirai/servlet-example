package com.example.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SchedulerEnqueueTest {

  private static Scheduler scheduler(FakeJobRepository repo, HandlerRegistry handlers) {
    SchedulerConfig c = new SchedulerConfig();
    c.workers = 1;
    c.pollMillis = 60_000; // only wakeup() can make the job run in time
    c.stopMillis = 2000;
    return new Scheduler(repo, handlers, Clock.systemUTC(), "n1", c);
  }

  @Test
  void enqueueRunsAOneTimeJobAtOnceWithItsContext() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    AtomicReference<JsonObject> seen = new AtomicReference<>();
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register(
        "h",
        ctx -> {
          seen.set(ctx);
          return ctx;
        });
    Scheduler s = scheduler(repo, handlers);
    s.start();
    Thread.sleep(100); // let the first poll finish, so the next one is 60s away
    JsonObject input = new JsonObject();
    input.addProperty("userId", 42);
    String id = s.enqueue("report", Job.JAVA, "h", input);
    long end = System.currentTimeMillis() + 5000;
    while (repo.row(id) != null && System.currentTimeMillis() < end) {
      Thread.sleep(10);
    }
    s.stop();
    assertNull(repo.row(id), "a successful once job is deleted");
    assertEquals(42, seen.get().get("userId").getAsInt());
  }

  @Test
  void getReturnsTheRunningSchedulerOnly() {
    Scheduler s = scheduler(new FakeJobRepository(), new HandlerRegistry(n -> null));
    s.start();
    assertSame(s, Scheduler.get());
    s.stop();
    assertNull(Scheduler.get());
  }
}
