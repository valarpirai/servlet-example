package com.example.record;

import com.example.config.Database;
import com.example.model.BusinessRule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;

/**
 * Creates, reads, changes and deletes business rules. Java code uses {@code new BusinessRules()}.
 * Scripts use the {@code BusinessRules} global. Every write goes through {@link TableRecord}, so
 * rules on {@code business_rules} itself still run.
 */
public class BusinessRules {

  private static final String TABLE = "business_rules";
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  private final Database database;

  /** Uses the application database, found on first call. */
  public BusinessRules() {
    this(null);
  }

  /**
   * Uses a given database.
   *
   * @param database the database, or null for the application database
   */
  public BusinessRules(Database database) {
    this.database = database;
  }

  /**
   * Checks a rule without saving it. The script and condition must compile.
   *
   * @param rule the rule to check
   * @throws IllegalArgumentException when a field is missing or invalid, or the script has a syntax
   *     error
   */
  public void validate(BusinessRule rule) {
    if (isBlank(rule.getName())) {
      throw new IllegalArgumentException("Business rule needs a name");
    }
    if (rule.getCollection() == null || !IDENTIFIER.matcher(rule.getCollection()).matches()) {
      throw new IllegalArgumentException("Invalid collection: " + rule.getCollection());
    }
    if (!"before".equals(rule.getRunWhen()) && !"after".equals(rule.getRunWhen())) {
      throw new IllegalArgumentException("runWhen must be 'before' or 'after'");
    }
    if (!rule.isOnInsert() && !rule.isOnUpdate() && !rule.isOnDelete()) {
      throw new IllegalArgumentException(
          "Business rule needs at least one of onInsert, onUpdate, onDelete");
    }
    if (isBlank(rule.getScript())) {
      throw new IllegalArgumentException("Business rule needs a script");
    }
    compile(rule);
  }

  /**
   * Checks a rule given as a script object, without saving it.
   *
   * @param fields rule fields by bean name
   * @throws IllegalArgumentException when a field is missing or invalid, or the script has a syntax
   *     error
   */
  public void validate(Map<String, Object> fields) {
    validate(apply(new BusinessRule(), fields));
  }

  /**
   * Validates and saves a new rule.
   *
   * @param rule the rule, without an id
   * @return the new id, or null when a rule on {@code business_rules} aborted the insert
   */
  public String create(BusinessRule rule) {
    validate(rule);
    TableRecord record = record();
    write(record, rule);
    return record.insert();
  }

  /**
   * Validates and saves a new rule given as a script object.
   *
   * @param fields rule fields by bean name: name, collection, runWhen, onInsert, onUpdate,
   *     onDelete, condition, script, orderNo, active
   * @return the new id, or null when aborted
   */
  public String create(Map<String, Object> fields) {
    return create(apply(new BusinessRule(), fields));
  }

  /**
   * Reads one rule.
   *
   * @param id the rule id
   * @return the rule, or null when none has that id
   */
  public BusinessRule get(String id) {
    TableRecord record = find(id);
    return record == null ? null : read(record);
  }

  /**
   * Lists rules in run order: table, then {@code orderNo}, then id.
   *
   * @param collection the table, or null for every table
   * @return the rules, possibly empty
   */
  public List<BusinessRule> list(String collection) {
    TableRecord record = record();
    if (!isBlank(collection)) {
      record.addQuery("collection", collection);
    }
    record.orderBy("collection");
    record.orderBy("order_no");
    record.orderBy("id");
    record.query();
    List<BusinessRule> rules = new ArrayList<>();
    while (record.next()) {
      rules.add(read(record));
    }
    return rules;
  }

  /**
   * Changes fields of a rule. The result must still be valid.
   *
   * @param id the rule id
   * @param changes fields to change, by bean name
   * @return true when saved, false when a rule aborted the update
   * @throws IllegalArgumentException when no rule has the id or the result is invalid
   */
  public boolean update(String id, Map<String, Object> changes) {
    TableRecord record = find(id);
    if (record == null) {
      throw new IllegalArgumentException("No business rule with id " + id);
    }
    BusinessRule merged = apply(read(record), changes);
    validate(merged);
    write(record, merged);
    return record.update();
  }

  /**
   * Turns a rule on or off.
   *
   * @param id the rule id
   * @param active true to run the rule
   * @return true when saved
   */
  public boolean setActive(String id, boolean active) {
    return update(id, Map.of("active", active));
  }

  /**
   * Deletes a rule.
   *
   * @param id the rule id
   * @return true when deleted, false when no rule has the id or a rule aborted the delete
   */
  public boolean delete(String id) {
    TableRecord record = find(id);
    return record != null && record.deleteRecord();
  }

  // ---- internals ----

  private TableRecord record() {
    return database == null ? new TableRecord(TABLE) : new TableRecord(database, TABLE);
  }

  private TableRecord find(String id) {
    long key;
    try {
      key = Long.parseLong(String.valueOf(id).trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid business rule id: " + id);
    }
    TableRecord record = record();
    record.addQuery("id", key);
    record.query();
    return record.next() ? record : null;
  }

  private static void write(TableRecord record, BusinessRule rule) {
    record.setValue("name", rule.getName());
    record.setValue("collection", rule.getCollection());
    record.setValue("run_when", rule.getRunWhen());
    record.setValue("on_insert", rule.isOnInsert());
    record.setValue("on_update", rule.isOnUpdate());
    record.setValue("on_delete", rule.isOnDelete());
    record.setValue("condition", isBlank(rule.getCondition()) ? null : rule.getCondition());
    record.setValue("script", rule.getScript());
    record.setValue("order_no", rule.getOrderNo());
    record.setValue("active", rule.isActive());
  }

  private static BusinessRule read(TableRecord record) {
    return BusinessRule.builder()
        .id(record.getValue("id"))
        .name(record.getValue("name"))
        .collection(record.getValue("collection"))
        .runWhen(record.getValue("run_when"))
        .onInsert(Boolean.parseBoolean(record.getValue("on_insert")))
        .onUpdate(Boolean.parseBoolean(record.getValue("on_update")))
        .onDelete(Boolean.parseBoolean(record.getValue("on_delete")))
        .condition(record.getValue("condition"))
        .script(record.getValue("script"))
        .orderNo(parseInt(record.getValue("order_no"), 100))
        .active(Boolean.parseBoolean(record.getValue("active")))
        .build();
  }

  /** Copies the named fields onto the rule. Unknown names are an error. */
  private static BusinessRule apply(BusinessRule rule, Map<String, Object> fields) {
    Map<String, Object> remaining = new LinkedHashMap<>(fields);
    take(remaining, "name", v -> rule.setName(text(v)));
    take(remaining, "collection", v -> rule.setCollection(text(v)));
    take(remaining, "runWhen", v -> rule.setRunWhen(text(v)));
    take(remaining, "onInsert", v -> rule.setOnInsert(flag(v)));
    take(remaining, "onUpdate", v -> rule.setOnUpdate(flag(v)));
    take(remaining, "onDelete", v -> rule.setOnDelete(flag(v)));
    take(remaining, "condition", v -> rule.setCondition(text(v)));
    take(remaining, "script", v -> rule.setScript(text(v)));
    take(remaining, "orderNo", v -> rule.setOrderNo(parseInt(text(v), 100)));
    take(remaining, "active", v -> rule.setActive(flag(v)));
    if (!remaining.isEmpty()) {
      throw new IllegalArgumentException("Unknown business rule fields: " + remaining.keySet());
    }
    return rule;
  }

  private static void take(
      Map<String, Object> fields, String name, java.util.function.Consumer<Object> setter) {
    if (fields.containsKey(name)) {
      setter.accept(fields.remove(name));
    }
  }

  /** Compiles the source the runner would run. A syntax error fails here, not at write time. */
  private static void compile(BusinessRule rule) {
    String source =
        isBlank(rule.getCondition())
            ? rule.getScript()
            : "if (" + rule.getCondition() + ") {\n" + rule.getScript() + "\n}";
    Context cx = Context.enter();
    try {
      cx.setOptimizationLevel(-1);
      cx.compileString(source, "business rule '" + rule.getName() + "'", 1, null);
    } catch (RhinoException e) {
      throw new IllegalArgumentException("Business rule does not compile: " + e.getMessage(), e);
    } finally {
      Context.exit();
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String text(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private static boolean flag(Object v) {
    return v instanceof Boolean b ? b : Boolean.parseBoolean(text(v));
  }

  private static int parseInt(String v, int fallback) {
    try {
      return v == null ? fallback : (int) Double.parseDouble(v);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
