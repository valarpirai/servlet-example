package com.example.record;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
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
import org.jooq.impl.DSL;

/**
 * Query and write rows of a table, and run business rules around each write. Scripts reach it as
 * {@code new TableRecord('table')}. Values read and written are strings or numbers.
 */
public class TableRecord {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Set<String> OPERATORS = Set.of("=", "!=", ">", "<", ">=", "<=", "LIKE");

  private final Database database;
  private final String table;
  private String keyColumn;
  private final List<Condition> conditions = new ArrayList<>();
  private final List<org.jooq.OrderField<?>> ordering = new ArrayList<>();
  private int limit;
  private List<Map<String, Object>> rows = List.of();
  private int index = -1;
  private Map<String, Object> current = new LinkedHashMap<>();
  private Map<String, Object> previous;
  private boolean aborted;

  /**
   * Creates a record handle on the application database.
   *
   * @param table name of a table; when {@code record.allowedTables} is set it must be listed
   */
  public TableRecord(String table) {
    this(DbPropertiesLoader.getDatabase(), table);
  }

  /**
   * Creates a record handle on a given database.
   *
   * @param database database to use
   * @param table name of a table; when {@code record.allowedTables} is set it must be listed
   */
  public TableRecord(Database database, String table) {
    if (database == null) {
      throw new IllegalStateException("TableRecord needs the database, which is not ready");
    }
    this.database = database;
    this.table = table;
    if (table == null || !IDENTIFIER.matcher(table).matches()) {
      throw new IllegalArgumentException("Invalid table name: " + table);
    }
    if (!AllowedTables.current().usable(table)) {
      throw new IllegalArgumentException("Table is not allowed: " + table);
    }
  }

  /**
   * Finds the key column on first use: the one the setting lists, else the table's primary key.
   * Reads never call this.
   */
  private String key() {
    if (keyColumn == null) {
      keyColumn =
          AllowedTables.current()
              .keyFor(table)
              .or(() -> database.primaryKey(table))
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Table " + table + " has no single-column primary key; it is read-only"));
    }
    return keyColumn;
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
   * Sorts the result ascending. Call again to add a lower-priority sort column.
   *
   * @param column column name
   */
  public void orderBy(String column) {
    ordering.add(column(column).asc());
  }

  /**
   * Sorts the result descending. Call again to add a lower-priority sort column.
   *
   * @param column column name
   */
  public void orderByDesc(String column) {
    ordering.add(column(column).desc());
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
      rows = fetchRows();
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
   * Names the key column writes use for this table.
   *
   * @return the column the setting lists, else the table's single-column primary key
   * @throws IllegalStateException when the table has no single-column primary key
   */
  public String getKeyColumn() {
    return key();
  }

  /**
   * Lists the columns of the current row.
   *
   * @return column names in query order; empty before the first {@link #next()}
   */
  public List<String> getColumnNames() {
    return new ArrayList<>(current.keySet());
  }

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
    key();
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
                      .returningResult(column(key()))
                      .fetchOne()
                      .value1());
      current.put(key(), key);
    } catch (Exception e) {
      throw new IllegalStateException("Insert into " + table + " failed: " + e.getMessage(), e);
    }
    rules.run(table, BusinessRuleRunner.Operation.INSERT, "after", this);
    return String.valueOf(current.get(key()));
  }

  /**
   * Writes the changed fields of the current row.
   *
   * @return true when written, false when a before rule aborted
   */
  public boolean update() {
    key();
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
                    .where(column(key()).eq(previous.get(key())))
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
    key();
    requireLoadedRow();
    BusinessRuleRunner rules = new BusinessRuleRunner(database);
    if (!rules.run(table, BusinessRuleRunner.Operation.DELETE, "before", this)) {
      return false;
    }
    try {
      database.query(
          dsl ->
              dsl.deleteFrom(table(name(table)))
                  .where(column(key()).eq(previous.get(key())))
                  .execute());
    } catch (Exception e) {
      throw new IllegalStateException("Delete from " + table + " failed: " + e.getMessage(), e);
    }
    rules.run(table, BusinessRuleRunner.Operation.DELETE, "after", this);
    return true;
  }

  // ---- bulk writes ----

  /**
   * Counts the rows that match the conditions.
   *
   * @return the row count
   */
  public int count() {
    try {
      return database.query(
          dsl -> dsl.fetchCount(dsl.selectFrom(table(name(table))).where(conditions)));
    } catch (Exception e) {
      throw new IllegalStateException("Count on " + table + " failed: " + e.getMessage(), e);
    }
  }

  /**
   * Writes the fields set with {@link #setValue} to every row that matches the conditions. Each row
   * runs its business rules. The write repeats the conditions, so a row that changed since it was
   * read is skipped. That makes the call a safe compare-and-set: the returned count says how many
   * rows this caller won.
   *
   * @return rows updated; rows skipped by a before rule or a lost race are not counted
   */
  public int updateMultiple() {
    key();
    requireConditions("updateMultiple");
    Map<String, Object> values = new LinkedHashMap<>(current);
    BusinessRuleRunner rules = new BusinessRuleRunner(database);
    int updated = 0;
    for (Map<String, Object> row : matchingRows()) {
      TableRecord one = loaded(row);
      values.forEach(one::setValue);
      if (!rules.run(table, BusinessRuleRunner.Operation.UPDATE, "before", one)) {
        continue;
      }
      Map<String, Object> changed = one.changedFields();
      if (!changed.isEmpty() && writeIfStillMatching(row, changed) == 0) {
        continue;
      }
      rules.run(table, BusinessRuleRunner.Operation.UPDATE, "after", one);
      updated++;
    }
    return updated;
  }

  /**
   * Deletes every row that matches the conditions. Each row runs its business rules. The delete
   * repeats the conditions, so a row that changed since it was read is skipped.
   *
   * @return rows deleted
   */
  public int deleteMultiple() {
    key();
    requireConditions("deleteMultiple");
    BusinessRuleRunner rules = new BusinessRuleRunner(database);
    int deleted = 0;
    for (Map<String, Object> row : matchingRows()) {
      TableRecord one = loaded(row);
      if (!rules.run(table, BusinessRuleRunner.Operation.DELETE, "before", one)) {
        continue;
      }
      int n;
      try {
        n =
            database.query(
                dsl ->
                    dsl.deleteFrom(table(name(table)))
                        .where(column(key()).eq(row.get(key())))
                        .and(DSL.and(conditions))
                        .execute());
      } catch (Exception e) {
        throw new IllegalStateException("Delete from " + table + " failed: " + e.getMessage(), e);
      }
      if (n == 0) {
        continue;
      }
      rules.run(table, BusinessRuleRunner.Operation.DELETE, "after", one);
      deleted++;
    }
    return deleted;
  }

  private void requireConditions(String method) {
    if (conditions.isEmpty()) {
      throw new IllegalStateException(method + " needs at least one addQuery condition");
    }
  }

  private List<Map<String, Object>> matchingRows() {
    try {
      return fetchRows();
    } catch (Exception e) {
      throw new IllegalStateException("Query on " + table + " failed: " + e.getMessage(), e);
    }
  }

  private List<Map<String, Object>> fetchRows() throws Exception {
    return database.query(
        dsl -> {
          SelectConditionStep<?> select = dsl.select().from(table(name(table))).where(conditions);
          var ordered = ordering.isEmpty() ? select : select.orderBy(ordering);
          return (limit > 0 ? ordered.limit(limit) : ordered).fetch().intoMaps();
        });
  }

  /** A record positioned on a row, as if {@link #next()} had just returned it. */
  private TableRecord loaded(Map<String, Object> row) {
    TableRecord one = new TableRecord(database, table);
    one.current = new LinkedHashMap<>(row);
    one.previous = new HashMap<>(row);
    return one;
  }

  /** Fields whose value now differs from the row as it was read. */
  private Map<String, Object> changedFields() {
    Map<String, Object> changed = new LinkedHashMap<>();
    current.forEach(
        (k, v) -> {
          if (!Objects.equals(text(v), text(previous.get(k)))) {
            changed.put(k, v);
          }
        });
    return changed;
  }

  /** Updates one row by key and repeats the query conditions, so the write is a compare-and-set. */
  private int writeIfStillMatching(Map<String, Object> row, Map<String, Object> changed) {
    try {
      return database.query(
          dsl ->
              dsl.update(table(name(table)))
                  .set(fields(changed))
                  .where(column(key()).eq(row.get(key())))
                  .and(DSL.and(conditions))
                  .execute());
    } catch (Exception e) {
      throw new IllegalStateException("Update of " + table + " failed: " + e.getMessage(), e);
    }
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
