package com.example.record;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.example.module.ModuleManager;
import com.example.script.ScriptExecutor;
import com.example.script.ScriptModuleLoader;
import com.example.util.PropertiesUtil;
import java.io.IOException;
import java.util.List;
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
  private final ScriptExecutor executor = new ScriptExecutor();

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
    boolean hasCondition = condition != null && !condition.isBlank();
    String source = hasCondition ? "if (" + condition + ") {\n" + script + "\n}" : script;
    try {
      source = new ScriptModuleLoader(new ModuleManager(database)).prepareWithModules(source);
    } catch (IOException e) {
      throw new IllegalStateException("Business rule '" + name + "' failed: " + e.getMessage(), e);
    }
    TableRecord previous = current.previousSnapshot();

    ScriptExecutor.ScriptExecutionResult result =
        executor.execute(
            source,
            (scope, cx) -> {
              ScriptableObject.putProperty(scope, "current", Context.javaToJS(current, scope));
              ScriptableObject.putProperty(
                  scope, "previous", previous == null ? null : Context.javaToJS(previous, scope));
              ScriptableObject.putProperty(scope, "TableRecord", new RecordConstructor(database));
            });
    if (!result.isSuccess()) {
      throw new IllegalStateException(
          "Business rule '" + name + "' failed: " + result.getMessage(), result.getThrowable());
    }
  }
}
