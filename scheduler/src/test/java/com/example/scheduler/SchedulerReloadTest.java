package com.example.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import com.example.model.Job;
import com.example.repository.FakeJobRepository;
import com.google.gson.JsonObject;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SchedulerReloadTest {

  private static SchedulerConfig config(int workers) {
    SchedulerConfig c = new SchedulerConfig();
    c.workers = workers;
    c.pollMillis = 20;
    c.stopMillis = 2000;
    return c;
  }

  private static Scheduler scheduler(
      FakeJobRepository repo, HandlerRegistry handlers, int workers) {
    return new Scheduler(repo, handlers, Clock.systemUTC(), "n1", config(workers));
  }

  private static long liveWorkerThreads() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(t -> t.getName().startsWith("bg-scheduler-worker-") && t.isAlive())
        .count();
  }

  private static void awaitWorkers(Scheduler s, int expected) throws InterruptedException {
    long end = System.currentTimeMillis() + 3000;
    while (s.workerCount() != expected && System.currentTimeMillis() < end) {
      Thread.sleep(10);
    }
    assertEquals(expected, s.workerCount());
  }

  @Test
  void growingStartsMoreWorkers() throws Exception {
    Scheduler s = scheduler(new FakeJobRepository(), new HandlerRegistry(n -> null), 1);
    s.start();
    try {
      assertEquals(1, s.workerCount());
      String summary = s.reload(config(3));
      assertEquals(3, s.workerCount());
      assertTrue(summary.contains("workers 1 -> 3"), summary);
    } finally {
      s.stop();
    }
  }

  @Test
  void shrinkingStopsIdleWorkersAtOnce() throws Exception {
    Scheduler s = scheduler(new FakeJobRepository(), new HandlerRegistry(n -> null), 3);
    s.start();
    try {
      s.reload(config(1));
      awaitWorkers(s, 1);
      long end = System.currentTimeMillis() + 3000;
      while (liveWorkerThreads() > 1 && System.currentTimeMillis() < end) {
        Thread.sleep(10);
      }
      assertEquals(1, liveWorkerThreads());
    } finally {
      s.stop();
    }
  }

  @Test
  void shrinkingNeverInterruptsARunningJob() throws Exception {
    FakeJobRepository repo = new FakeJobRepository();
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    boolean[] interrupted = {false};
    HandlerRegistry handlers = new HandlerRegistry(n -> null);
    handlers.register(
        "slow",
        ctx -> {
          started.countDown();
          try {
            release.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            interrupted[0] = true;
          }
          return ctx;
        });
    Scheduler s = scheduler(repo, handlers, 2);
    s.start();
    try {
      String id = s.enqueue("slow", Job.JAVA, "slow", new JsonObject());
      assertTrue(started.await(3, TimeUnit.SECONDS));
      s.reload(config(1));
      Thread.sleep(150);
      release.countDown();
      long end = System.currentTimeMillis() + 3000;
      while (repo.row(id) != null && System.currentTimeMillis() < end) {
        Thread.sleep(10);
      }
      assertNull(repo.row(id), "the job finished");
      assertFalse(interrupted[0], "the running job was not interrupted");
    } finally {
      s.stop();
    }
  }

  @Test
  void workerCountIsClamped() throws Exception {
    Scheduler s = scheduler(new FakeJobRepository(), new HandlerRegistry(n -> null), 2);
    s.start();
    try {
      s.reload(config(0));
      assertEquals(1, s.workerCount());
      s.reload(config(1000));
      assertEquals(Scheduler.MAX_WORKERS, s.workerCount());
    } finally {
      s.stop();
    }
  }

  @Test
  void reloadOnAStoppedSchedulerChangesNothing() {
    Scheduler s = scheduler(new FakeJobRepository(), new HandlerRegistry(n -> null), 2);
    assertTrue(s.reload(config(5)).contains("not running"));
  }

  @Test
  void adminReportsWhenNoSchedulerRuns() {
    assertTrue(new SchedulerAdmin().reload().contains("No scheduler"));
  }
}
