package com.example.record;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.example.util.PropertiesUtil;
import java.io.IOException;
import java.util.List;
import java.util.ServiceLoader;
import org.jooq.Field;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the active rules for a table and write, then runs each in the script sandbox. A rule sees
 * {@code current}, {@code previous} (null on insert) and the {@code TableRecord} constructor.
 */
class BusinessRuleRunner {

  enum Operation {
    INSERT("on_insert"),
    UPDATE("on_update"),
    DELETE("on_delete");

    private final String column;

    Operation(String column) {
      this.column = column;
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(BusinessRuleRunner.class);

  // Rules that write records fire more rules. This counts how deep the current thread is.
  private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

  private static final Field<String> NAME = field("name", String.class);
  private static final Field<String> CONDITION = field("condition", String.class);
  private static final Field<String> SCRIPT = field("script", String.class);

  private final Database database;
  private final RuleScriptEngine engine = engine();

  private static volatile RuleScriptEngine sharedEngine;

  private static RuleScriptEngine engine() {
    if (sharedEngine == null) {
      sharedEngine =
          ServiceLoader.load(RuleScriptEngine.class)
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("No rule script engine is registered"));
    }
    return sharedEngine;
  }

  BusinessRuleRunner(Database database) {
    this.database = database;
  }

  /**
   * Runs the matching rules in order.
   *
   * @param tableName table being written
   * @param operation the write
   * @param when {@code before} or {@code after}
   * @param current the record being written
   * @return false when a before rule aborted the write
   */
  boolean run(String tableName, Operation operation, String when, TableRecord current) {
    boolean proceed = runSync(tableName, operation, when, current);
    if (proceed && when.equals("after")) {
      dispatchAsync(tableName, operation, current);
    }
    return proceed;
  }

  private boolean runSync(String tableName, Operation operation, String when, TableRecord current) {
    List<org.jooq.Record3<String, String, String>> rules = load(tableName, operation, when);
    if (rules.isEmpty()) {
      return true;
    }
    int maxDepth = PropertiesUtil.getInt("record.maxRuleDepth", 5);
    if (DEPTH.get() >= maxDepth) {
      throw new IllegalStateException("Business rules nest deeper than " + maxDepth);
    }
    DEPTH.set(DEPTH.get() + 1);
    try {
      for (var rule : rules) {
        execute(rule.value1(), rule.value2(), rule.value3(), current);
        if (when.equals("before") && current.isAborted()) {
          logger.info("Business rule '{}' aborted {} on {}", rule.value1(), operation, tableName);
          return false;
        }
      }
      return true;
    } finally {
      DEPTH.set(DEPTH.get() - 1);
    }
  }

  private List<org.jooq.Record3<String, String, String>> load(
      String tableName, Operation operation, String when) {
    try {
      return database.query(
          dsl ->
              dsl.select(NAME, CONDITION, SCRIPT)
                  .from(table("business_rules"))
                  .where(field("collection", String.class).eq(tableName))
                  .and(field("run_when", String.class).eq(when))
                  .and(field(operation.column, Boolean.class).isTrue())
                  .and(field("active", Boolean.class).isTrue())
                  .orderBy(field("order_no"), field("id"))
                  .fetch());
    } catch (Exception e) {
      throw new IllegalStateException("Loading business rules failed: " + e.getMessage(), e);
    }
  }

  private void execute(String name, String condition, String script, TableRecord current) {
    execute(name, condition, script, current, current.previousSnapshot());
  }

  /**
   * Queues each async rule that matches a finished write. The write is done, so nothing here may
   * fail it: every problem is logged and the rule is skipped. The rule's condition is checked now,
   * against the saved fields, so a rule that does not apply costs no job.
   */
  private void dispatchAsync(String tableName, Operation operation, TableRecord current) {
    List<org.jooq.Record4<Object, String, String, String>> rules;
    try {
      rules = loadAsync(tableName, operation);
    } catch (RuntimeException e) {
      logger.warn("Loading async rules for {} failed: {}", tableName, e.getMessage());
      return;
    }
    if (rules.isEmpty()) {
      return;
    }
    int depth = AsyncRules.depth() + 1;
    int maxDepth = PropertiesUtil.getInt("record.maxRuleDepth", 5);
    if (depth > maxDepth) {
      logger.warn("Async rules on {} nest deeper than {}; skipped", tableName, maxDepth);
      return;
    }
    for (var rule : rules) {
      String id = String.valueOf(rule.value1());
      String name = rule.value2();
      try {
        TableRecord previous = current.previousSnapshot();
        if (!conditionHolds(name, rule.value3(), current, previous)) {
          continue;
        }
        AsyncRuleCall call = new AsyncRuleCall();
        call.ruleId = id;
        call.ruleName = name;
        call.table = tableName;
        call.operation = operation.name().toLowerCase();
        call.current = current.currentText();
        call.previous = current.previousText();
        call.depth = depth;
        AsyncRules.enqueue(call);
      } catch (RuntimeException e) {
        logger.warn("Async rule '{}' on {} was not queued: {}", name, tableName, e.getMessage());
      }
    }
  }

  private List<org.jooq.Record4<Object, String, String, String>> loadAsync(
      String tableName, Operation operation) {
    try {
      return database.query(
          dsl ->
              dsl.select(field("id"), NAME, CONDITION, SCRIPT)
                  .from(table("business_rules"))
                  .where(field("collection", String.class).eq(tableName))
                  .and(field("run_when", String.class).eq("async"))
                  .and(field(operation.column, Boolean.class).isTrue())
                  .and(field("active", Boolean.class).isTrue())
                  .orderBy(field("order_no"), field("id"))
                  .fetch());
    } catch (Exception e) {
      throw new IllegalStateException("Loading async rules failed: " + e.getMessage(), e);
    }
  }

  private boolean conditionHolds(
      String name, String condition, TableRecord current, TableRecord previous) {
    if (condition == null || condition.isBlank()) {
      return true;
    }
    RuleScriptEngine.Outcome result =
        engine.evaluate(
            "(" + condition + ") ? true : false",
            (scope, cx) -> bind(scope, cx, current, previous));
    if (!result.isSuccess()) {
      throw new IllegalStateException(
          "condition failed: " + result.getMessage(), result.getThrowable());
    }
    return Boolean.TRUE.equals(result.getResult());
  }

  private void bind(
      org.mozilla.javascript.Scriptable scope,
      Context cx,
      TableRecord current,
      TableRecord previous) {
    ScriptableObject.putProperty(scope, "current", Context.javaToJS(current, scope));
    ScriptableObject.putProperty(
        scope, "previous", previous == null ? null : Context.javaToJS(previous, scope));
    ScriptableObject.putProperty(scope, "TableRecord", new RecordConstructor(database));
  }

  /**
   * Runs one async rule's script on saved fields. The job calls this, on a scheduler worker.
   *
   * @param call the saved write
   * @param script the rule's script
   */
  void executeAsync(AsyncRuleCall call, String script) {
    TableRecord current = TableRecord.snapshot(database, call.table, call.current);
    TableRecord previous =
        call.previous == null ? null : TableRecord.snapshot(database, call.table, call.previous);
    execute(call.ruleName, null, script, current, previous);
  }

  private void execute(
      String name, String condition, String script, TableRecord current, TableRecord previous) {
    boolean hasCondition = condition != null && !condition.isBlank();
    String source = hasCondition ? "if (" + condition + ") {\n" + script + "\n}" : script;
    try {
      source = engine.prepareModules(source, database);
    } catch (IOException e) {
      throw new IllegalStateException("Business rule '" + name + "' failed: " + e.getMessage(), e);
    }
    RuleScriptEngine.Outcome result =
        engine.evaluate(source, (scope, cx) -> bind(scope, cx, current, previous));
    if (!result.isSuccess()) {
      throw new IllegalStateException(
          "Business rule '" + name + "' failed: " + result.getMessage(), result.getThrowable());
    }
  }
}
