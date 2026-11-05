package com.example.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory queue of claimed jobs. Lower priority runs first, then earlier due time. {@link #take}
 * blocks, so idle workers cost nothing. After {@link #close}, {@code take} returns null at once and
 * queued jobs wait for {@link #drain}.
 */
public class JobQueue {

  private static final Comparator<Job> ORDER =
      Comparator.<Job>comparingInt(j -> j.priority)
          .thenComparing(j -> j.nextRun, Comparator.nullsFirst(Comparator.naturalOrder()));

  private final PriorityQueue<Job> jobs = new PriorityQueue<>(ORDER);
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition notEmpty = lock.newCondition();
  private boolean closed;

  /** Adds a job. Ignored after close. */
  public void offer(Job job) {
    lock.lock();
    try {
      if (!closed) {
        jobs.add(job);
        notEmpty.signal();
      }
    } finally {
      lock.unlock();
    }
  }

  /** Waits for the next job. Returns null once the queue is closed. */
  public Job take() throws InterruptedException {
    lock.lockInterruptibly();
    try {
      while (jobs.isEmpty() && !closed) {
        notEmpty.await();
      }
      return closed ? null : jobs.poll();
    } finally {
      lock.unlock();
    }
  }

  /** Returns the next job now, or null when none is waiting. */
  public Job poll() {
    lock.lock();
    try {
      return jobs.poll();
    } finally {
      lock.unlock();
    }
  }

  public int size() {
    lock.lock();
    try {
      return jobs.size();
    } finally {
      lock.unlock();
    }
  }

  /** Stops the queue and wakes every waiting worker. Jobs still queued stay for {@link #drain}. */
  public void close() {
    lock.lock();
    try {
      closed = true;
      notEmpty.signalAll();
    } finally {
      lock.unlock();
    }
  }

  /** Removes and returns every job still queued, so the caller can hand them back. */
  public List<Job> drain() {
    lock.lock();
    try {
      List<Job> out = new ArrayList<>(jobs);
      jobs.clear();
      return out;
    } finally {
      lock.unlock();
    }
  }
}
