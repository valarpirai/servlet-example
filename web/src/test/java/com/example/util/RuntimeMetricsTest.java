package com.example.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RuntimeMetricsTest {

  @AfterEach
  void reset() {
    RuntimeMetrics.setWorkerExecutor(null);
  }

  @Test
  void reportsRequestsMemoryAndThreads() {
    Map<String, Object> m = RuntimeMetrics.snapshot(7);

    assertEquals(7L, m.get("totalRequests"));
    assertTrue(m.containsKey("memory"));
    assertTrue(((Map<?, ?>) m.get("threads")).containsKey("live"));
  }

  @Test
  void reportsWorkerPoolWhenRegistered() {
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(2, 8, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    RuntimeMetrics.setWorkerExecutor(pool);

    Map<?, ?> workers = (Map<?, ?>) RuntimeMetrics.snapshot(0).get("workers");

    assertEquals(8, workers.get("maxSize"));
    assertEquals(0, workers.get("queued"));
    pool.shutdown();
  }

  @Test
  void workersAndDbAreEmptyWithoutAPoolOrDatabase() {
    Map<String, Object> m = RuntimeMetrics.snapshot(0);

    assertTrue(((Map<?, ?>) m.get("workers")).isEmpty());
    assertFalse(((Map<?, ?>) m.get("db")).containsKey("pool"));
  }
}
