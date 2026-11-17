package com.example.record;

import com.example.config.DbPropertiesLoader;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async business rules: rules whose {@code run_when} is {@code async}. They run after the write,
 * off the request thread. {@code core} only describes the work. A module that can run jobs plugs in
 * a sink with {@link #setSink}, and the job calls {@link #run}.
 */
public final class AsyncRules {

  private static final Logger logger = LoggerFactory.getLogger(AsyncRules.class);

  private static volatile Consumer<AsyncRuleCall> sink;

  /** Async depth of the job running on this thread. Zero outside a job. */
  private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

  private AsyncRules() {}

  /**
   * Plugs in whatever saves an async run for later, for example a scheduler job.
   *
   * @param consumer receives each run, or null to remove the sink
   */
  public static void setSink(Consumer<AsyncRuleCall> consumer) {
    sink = consumer;
  }

  /**
   * Hands a run to the sink.
   *
   * @param call the run
   * @throws IllegalStateException when no sink is set
   */
  static void enqueue(AsyncRuleCall call) {
    Consumer<AsyncRuleCall> target = sink;
    if (target == null) {
      throw new IllegalStateException(
          "no sink for async rules; the scheduler module is not loaded");
    }
    target.accept(call);
  }

  /**
   * How deep the async chain is on this thread.
   *
   * @return zero outside a job, else the call's depth
   */
  static int depth() {
    return DEPTH.get();
  }

  /**
   * Runs a saved call. The job handler calls this. A rule that is gone, switched off, or no longer
   * async is skipped. A script that throws fails the run, so the job can retry.
   *
   * @param call the saved write and rule
   * @throws IllegalStateException when the script fails
   */
  public static void run(AsyncRuleCall call) {
    var database = DbPropertiesLoader.getDatabase();
    if (database == null) {
      throw new IllegalStateException("Async rules need the database, which is not ready");
    }
    TableRecord rule = new TableRecord(database, "business_rules");
    rule.addQuery("id", Long.parseLong(call.ruleId));
    rule.query();
    if (!rule.next()
        || !"async".equals(rule.getValue("run_when"))
        || !Boolean.parseBoolean(rule.getValue("active"))) {
      logger.info("Async rule {} ('{}') is gone or off; skipped", call.ruleId, call.ruleName);
      return;
    }
    int before = DEPTH.get();
    DEPTH.set(call.depth);
    try {
      new BusinessRuleRunner(database).executeAsync(call, rule.getValue("script"));
    } finally {
      DEPTH.set(before);
    }
  }
}
