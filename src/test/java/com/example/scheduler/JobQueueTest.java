package com.example.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JobQueueTest {

  private static Job job(String id, int priority, long dueSeconds) {
    Job j = new Job();
    j.id = id;
    j.priority = priority;
    j.nextRun = Instant.ofEpochSecond(dueSeconds);
    return j;
  }

  @Test
  void lowerPriorityThenEarlierDueRunsFirst() throws Exception {
    JobQueue q = new JobQueue();
    q.offer(job("late-normal", 100, 20));
    q.offer(job("early-normal", 100, 10));
    q.offer(job("urgent", 1, 30));
    assertEquals("urgent", q.take().id);
    assertEquals("early-normal", q.take().id);
    assertEquals("late-normal", q.take().id);
  }

  @Test
  void takeBlocksUntilAJobArrives() throws Exception {
    JobQueue q = new JobQueue();
    AtomicReference<Job> got = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);
    Thread t =
        new Thread(
            () -> {
              try {
                got.set(q.take());
              } catch (InterruptedException ignored) {
              }
              done.countDown();
            });
    t.start();
    q.offer(job("a", 1, 1));
    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> done.await());
    assertEquals("a", got.get().id);
  }

  @Test
  void closeWakesWaitersAndLeavesQueuedJobsForDrain() throws Exception {
    JobQueue q = new JobQueue();
    q.offer(job("a", 1, 1));
    q.close();
    assertNull(q.take());
    q.offer(job("ignored", 1, 1));
    assertEquals(1, q.drain().size());
    assertEquals(0, q.size());
  }
}
