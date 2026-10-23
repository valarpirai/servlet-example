package com.example.util;

import com.example.config.DbPropertiesLoader;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** Builds the /metrics report: memory, JVM threads, request worker threads and the DB pool. */
public final class RuntimeMetrics {

  private static volatile Executor workerExecutor;

  private RuntimeMetrics() {}

  /**
   * Register the pool that runs requests, so its load shows in the report.
   *
   * @param executor the web server's worker pool
   */
  public static void setWorkerExecutor(Executor executor) {
    workerExecutor = executor;
  }

  /**
   * Take a snapshot of the running application.
   *
   * @param totalRequests requests served since startup
   * @return the report, ready to turn into JSON
   */
  public static Map<String, Object> snapshot(long totalRequests) {
    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("totalRequests", totalRequests);
    metrics.put("memory", memory());
    metrics.put("threads", threads());
    metrics.put("workers", workers());
    metrics.put("db", db());
    metrics.put("timestamp", System.currentTimeMillis());
    return metrics;
  }

  private static Map<String, Object> memory() {
    Runtime runtime = Runtime.getRuntime();
    Map<String, Object> memory = new LinkedHashMap<>();
    memory.put("used", runtime.totalMemory() - runtime.freeMemory());
    memory.put("free", runtime.freeMemory());
    memory.put("total", runtime.totalMemory());
    memory.put("max", runtime.maxMemory());
    return memory;
  }

  private static Map<String, Object> threads() {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    Map<String, Object> threads = new LinkedHashMap<>();
    threads.put("active", Thread.activeCount());
    threads.put("live", bean.getThreadCount());
    threads.put("daemon", bean.getDaemonThreadCount());
    threads.put("peak", bean.getPeakThreadCount());
    return threads;
  }

  /**
   * Request worker pool. Tomcat's pool is not a java.util.concurrent pool, so the getters are read
   * by name. Empty when no pool is registered or it lacks them.
   */
  private static Map<String, Object> workers() {
    Map<String, Object> workers = new LinkedHashMap<>();
    Executor pool = workerExecutor;
    if (pool == null) {
      return workers;
    }
    try {
      workers.put("busy", call(pool, "getActiveCount"));
      workers.put("poolSize", call(pool, "getPoolSize"));
      workers.put("maxSize", call(pool, "getMaximumPoolSize"));
      workers.put("queued", ((java.util.Collection<?>) call(pool, "getQueue")).size());
      workers.put("completedTasks", call(pool, "getCompletedTaskCount"));
    } catch (ReflectiveOperationException | ClassCastException e) {
      workers.clear();
    }
    return workers;
  }

  private static Object call(Object target, String getter) throws ReflectiveOperationException {
    return target.getClass().getMethod(getter).invoke(target);
  }

  /** Connection pool load. Empty when there is no database or the pool has not started. */
  private static Map<String, Object> db() {
    var database = DbPropertiesLoader.getDatabase();
    Map<String, Object> pool = database == null ? null : database.poolStats();
    Map<String, Object> db = new LinkedHashMap<>();
    db.put("connected", database != null);
    if (pool != null) {
      db.put("pool", pool);
    }
    return db;
  }
}
