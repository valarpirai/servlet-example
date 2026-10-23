package com.example.record;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.util.PropertiesUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SelectConditionStep;

/**
 * Query and write rows of an allow-listed table, and run business rules around each write. Scripts
 * reach it as {@code new TableRecord('table')}. Values read and written are strings or numbers.
 */
public class TableRecord {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Set<String> OPERATORS = Set.of("=", "!=", ">", "<", ">=", "<=", "LIKE");

  private final Database database;
  private final String table;
  private final String keyColumn;
  private final List<Condition> conditions = new ArrayList<>();
  private String orderByColumn;
  private int limit;
  private List<Map<String, Object>> rows = List.of();
  private int index = -1;
  private Map<String, Object> current = new LinkedHashMap<>();
  private Map<String, Object> previous;
  private boolean aborted;

  /**
   * Creates a record handle on the application database.
   *
   * @param table name of an allow-listed table
   */
  public TableRecord(String table) {
    this(DbPropertiesLoader.getDatabase(), table);
  }

  TableRecord(Database database, String table) {
    if (database == null) {
      throw new IllegalStateException("TableRecord needs the database, which is not ready");
    }
    this.database = database;
    this.table = table;
    this.keyColumn = allowedKey(table);
  }

  /** Reads {@code record.allowedTables}, a list of {@code table:keyColumn} pairs. */
  private static String allowedKey(String table) {
    String allowed = PropertiesUtil.getString("record.allowedTables", "modules:name");
    for (String pair : allowed.split(",")) {
      String[] parts = pair.trim().split(":");
      if (parts.length == 2 && parts[0].equals(table)) {
        return parts[1];
      }
    }
    throw new IllegalArgumentException("Table is not allowed: " + table);
  }

  // ---- query ----

  /**
   * Adds an equality condition.
   *
   * @param column column name
   * @param value value to match
   */
  public void addQuery(String column, Object value) {
    addQuery(column, "=", value);
  }

  /**
   * Adds a condition.
   *
   * @param column column name
   * @param operator comparison: equals, not equals, greater, less, or LIKE (see OPERATORS)
   * @param value value to compare with
   */
  public void addQuery(String column, String operator, Object value) {
    Field<Object> f = column(column);
    Object v = normalize(value);
    String op = operator.toUpperCase();
    if (!OPERATORS.contains(op)) {
      throw new IllegalArgumentException("Operator is not allowed: " + operator);
    }
    conditions.add(
        switch (op) {
          case "=" -> f.eq(v);
          case "!=" -> f.ne(v);
          case ">" -> f.gt(v);
          case "<" -> f.lt(v);
          case ">=" -> f.ge(v);
          case "<=" -> f.le(v);
          default -> f.like(String.valueOf(v));
        });
  }

  /**
   * Sorts the result ascending.
   *
   * @param column column name
   */
  public void orderBy(String column) {
    column(column);
    this.orderByColumn = column;
  }

  /**
   * Caps the number of rows returned.
   *
   * @param limit maximum row count; 0 means no cap
   */
  public void setLimit(int limit) {
    this.limit = limit;
  }

  /** Runs the query. Call {@link #next()} to walk the rows. */
  public void query() {
    try {
      rows =
          database.query(
              dsl -> {
                SelectConditionStep<?> select =
                    dsl.select().from(table(name(table))).where(conditions);
                var ordered =
                    orderByColumn == null ? select : select.orderBy(column(orderByColumn));
                return (limit > 0 ? ordered.limit(limit) : ordered).fetch().intoMaps();
              });
    } catch (Exception e) {
      throw new IllegalStateException("Query on " + table + " failed: " + e.getMessage(), e);
    }
    index = -1;
  }

  /**
   * Moves to the next row of the last query.
   *
   * @return true when a row is now current
   */
  public boolean next() {
    index++;
    if (index >= rows.size()) {
      return false;
    }
    current = new LinkedHashMap<>(rows.get(index));
    previous = new HashMap<>(current);
    return true;
  }

  // ---- fields ----

  /**
   * Reads a field of the current row.
   *
   * @param column column name
   * @return the value as a string, or null
   */
  public String getValue(String column) {
    Object v = current.get(column);
    return v == null ? null : String.valueOf(v);
  }

  /**
   * Sets a field of the current row. Nothing is written until {@link #insert()} or {@link
   * #update()}.
   *
   * @param column column name
   * @param value new value
   */
  public void setValue(String column, Object value) {
    column(column);
    current.put(column, normalize(value));
  }

  /**
   * Tells whether a field differs from the row as it was read.
   *
   * @param column column name
   * @return true when the value changed, or when the row is new and the field is set
   */
  public boolean changes(String column) {
    Object before = previous == null ? null : previous.get(column);
    return !Objects.equals(text(current.get(column)), text(before));
  }

  /**
   * Stops the write. Only a before rule can stop it.
   *
   * @param abort true to stop the write
   */
  public void setAbortAction(boolean abort) {
    this.aborted = abort;
  }

  /**
   * Tells whether a before rule stopped the write.
   *
   * @return true when aborted
   */
  public boolean isAborted() {
    return aborted;
  }

  // ---- writes ----

  /**
   * Inserts the current fields as a new row.
   *
   * @return the new key, or null when a before rule aborted
   */
  public String insert() {
    previous = null;
    BusinessRuleRunner rules = new BusinessRuleRunner(database);
    if (!rules.run(table, BusinessRuleRunner.Operation.INSERT, "before", this)) {
      return null;
    }
    try {
      Object key =
          database.query(
              dsl ->
                  dsl.insertInto(table(name(table)))
                      .set(fields(current))
                      .returningResult(column(keyColumn))
                      .fetchOne()
                      .value1());
      current.put(keyColumn, key);
    } catch (Exception e) {
      throw new IllegalStateException("Insert into " + table + " failed: " + e.getMessage(), e);
    }
    rules.run(table, BusinessRuleRunner.Operation.INSERT, "after", this);
    return String.valueOf(current.get(keyColumn));
  }

  /**
   * Writes the changed fields of the current row.
   *
   * @return true when written, false when a before rule aborted
   */
  public boolean update() {
    requireLoadedRow();
    BusinessRuleRunner rules = new BusinessRuleRunner(database);
    if (!rules.run(table, BusinessRuleRunner.Operation.UPDATE, "before", this)) {
      return false;
    }
    Map<String, Object> changed = new LinkedHashMap<>();
    current.forEach(
        (k, v) -> {
          if (!Objects.equals(text(v), text(previous.get(k)))) {
            changed.put(k, v);
          }
        });
    if (!changed.isEmpty()) {
      try {
        database.query(
            dsl ->
                dsl.update(table(name(table)))
                    .set(fields(changed))
                    .where(column(keyColumn).eq(previous.get(keyColumn)))
                    .execute());
      } catch (Exception e) {
        throw new IllegalStateException("Update of " + table + " failed: " + e.getMessage(), e);
      }
    }
    rules.run(table, BusinessRuleRunner.Operation.UPDATE, "after", this);
    previous = new HashMap<>(current);
    return true;
  }

  /**
   * Deletes the current row.
   *
   * @return true when deleted, false when a before rule aborted
   */
  public boolean deleteRecord() {
    requireLoadedRow();
    BusinessRuleRunner rules = new BusinessRuleRunner(database);
    if (!rules.run(table, BusinessRuleRunner.Operation.DELETE, "before", this)) {
      return false;
    }
    try {
      database.query(
          dsl ->
              dsl.deleteFrom(table(name(table)))
                  .where(column(keyColumn).eq(previous.get(keyColumn)))
                  .execute());
    } catch (Exception e) {
      throw new IllegalStateException("Delete from " + table + " failed: " + e.getMessage(), e);
    }
    rules.run(table, BusinessRuleRunner.Operation.DELETE, "after", this);
    return true;
  }

  // ---- internals ----

  /** Read-only view of the row as it was loaded, for the {@code previous} script variable. */
  TableRecord previousSnapshot() {
    if (previous == null) {
      return null;
    }
    TableRecord snapshot = new TableRecord(database, table);
    snapshot.current = new LinkedHashMap<>(previous);
    snapshot.previous = new HashMap<>(previous);
    return snapshot;
  }

  private void requireLoadedRow() {
    if (previous == null) {
      throw new IllegalStateException("No current row. Call next() first.");
    }
  }

  private Field<Object> column(String column) {
    if (column == null || !IDENTIFIER.matcher(column).matches()) {
      throw new IllegalArgumentException("Invalid column name: " + column);
    }
    return field(name(column));
  }

  private Map<Field<?>, Object> fields(Map<String, Object> values) {
    Map<Field<?>, Object> out = new LinkedHashMap<>();
    values.forEach((k, v) -> out.put(column(k), v));
    return out;
  }

  /** Turns script values into plain Java: strings for text, long for whole numbers. */
  private static Object normalize(Object value) {
    if (value instanceof CharSequence) {
      return value.toString();
    }
    if (value instanceof Double d && d == Math.rint(d) && !Double.isInfinite(d)) {
      return d.longValue();
    }
    return value;
  }

  private static String text(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
