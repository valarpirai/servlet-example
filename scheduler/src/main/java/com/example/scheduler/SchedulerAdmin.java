package com.example.scheduler;

/**
 * The {@code Scheduler} global in scripts. A business rule on {@code app_properties} calls {@link
 * #reload()} when a {@code scheduler.*} setting changes.
 */
public class SchedulerAdmin {

  /**
   * Applies the {@code scheduler.*} settings to the scheduler on this node. Resizes the worker pool
   * without a restart. Other nodes are not touched.
   *
   * @return what changed, or a note that no scheduler runs on this node
   */
  public String reload() {
    Scheduler running = Scheduler.get();
    return running == null ? "No scheduler runs on this node" : running.reload();
  }
}
