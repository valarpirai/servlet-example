package com.example.tableapi;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.example.record.TableRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SortField;

/**
 * View of the tables in the {@code public} schema. A table name is used only after it matches a
 * name in {@code information_schema}, then it is quoted. Writes go through {@link TableEditor}.
 */
public class TableApi {

  /** Rows per page when the caller does not say. */
  static final int DEFAULT_PAGE_SIZE = 25;

  /** Most rows a page returns. */
  static final int MAX_PAGE_SIZE = 100;

  /** Column types with no useful order, so a list cannot sort on them. */
  private static final Set<String> UNSORTABLE_TYPES = Set.of("json", "xml", "bytea");

  /** Longest text a list cell carries. Row pages carry full text. */
  static final int LIST_CELL_MAX = 200;

  private static final String TABLES_SQL =
      "select t.table_name, count(c.column_name) as column_count"
          + " from information_schema.tables t"
          + " join information_schema.columns c"
          + " on c.table_schema = t.table_schema and c.table_name = t.table_name"
          + " where t.table_schema = 'public' and t.table_type = 'BASE TABLE'"
          + " group by t.table_name order by t.table_name";

  private static final String COLUMNS_SQL =
      "select column_name, data_type from information_schema.columns"
          + " where table_schema = 'public' and table_name = ? order by ordinal_position";

  private static final String KEY_SQL =
      "select kcu.column_name from information_schema.table_constraints tc"
          + " join information_schema.key_column_usage kcu"
          + " on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema"
          + " where tc.constraint_type = 'PRIMARY KEY' and tc.table_schema = 'public'"
          + " and tc.table_name = ? order by kcu.ordinal_position";

  private final Database database;

  public TableApi(Database database) {
    this.database = database;
  }

  /**
   * Lists every table.
   *
   * @return one map per table with {@code name} and {@code columns} (the column count)
   */
  public List<Map<String, Object>> listTables() throws Exception {
    return database.query(
        dsl ->
            dsl.fetch(TABLES_SQL).stream()
                .map(
                    r -> {
                      Map<String, Object> m = new LinkedHashMap<>();
                      m.put("name", r.get(0, String.class));
                      m.put("columns", r.get(1, Long.class));
                      return m;
                    })
                .toList());
  }

  /**
   * Reads one page of a table. Text is cut at {@value #LIST_CELL_MAX} characters.
   *
   * @param tableName table to read
   * @param page page number from 1; smaller values read page 1
   * @param size rows per page; 0 or less means {@value #DEFAULT_PAGE_SIZE}, and the most is {@value
   *     #MAX_PAGE_SIZE}
   * @param sort column to sort by, or null to sort by the key column
   * @param descending true to sort high to low
   * @return the page with columns, key column, total row count and rows, or empty when the table
   *     does not exist
   * @throws IllegalArgumentException when {@code sort} is not a sortable column of the table
   */
  public Optional<Map<String, Object>> rows(
      String tableName, int page, int size, String sort, boolean descending) throws Exception {
    if (!exists(tableName)) {
      return Optional.empty();
    }
    List<Map<String, Object>> columns = columns(tableName);
    String key = primaryKey(tableName);
    int pageNumber = Math.max(page, 1);
    int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    checkSortable(columns, sort);

    return database.query(
        dsl -> {
          List<SortField<?>> order = new ArrayList<>();
          if (sort != null) {
            Field<Object> f = field(name(sort));
            order.add(descending ? f.desc() : f.asc());
          }
          // The key breaks ties, so a row never lands on two pages.
          if (key != null && !key.equals(sort)) {
            order.add(field(name(key)).asc());
          }
          List<Map<String, Object>> rows =
              dsl
                  .select()
                  .from(table(name(tableName)))
                  .orderBy(order)
                  .limit(pageSize)
                  .offset((pageNumber - 1) * pageSize)
                  .fetch()
                  .stream()
                  .map(r -> toMap(r, LIST_CELL_MAX))
                  .toList();
          Long total = dsl.selectCount().from(table(name(tableName))).fetchOne(0, Long.class);

          Map<String, Object> out = new LinkedHashMap<>();
          out.put("table", tableName);
          out.put("columns", columns);
          out.put("primaryKey", key);
          out.put("writable", writable(tableName, key));
          out.put("total", total);
          out.put("page", pageNumber);
          out.put("size", pageSize);
          out.put("sort", sort);
          out.put("descending", sort != null && descending);
          out.put("rows", rows);
          return Optional.of(out);
        });
  }

  /** A table is writable when it is allow-listed with the key the database uses. */
  static boolean writable(String tableName, String primaryKey) {
    return primaryKey != null && primaryKey.equals(TableRecord.keyColumnOf(tableName));
  }

  private static void checkSortable(List<Map<String, Object>> columns, String sort) {
    if (sort == null) {
      return;
    }
    boolean ok =
        columns.stream()
            .anyMatch(c -> sort.equals(c.get("name")) && !UNSORTABLE_TYPES.contains(c.get("type")));
    if (!ok) {
      throw new IllegalArgumentException("Cannot sort by: " + sort);
    }
  }

  /**
   * Reads one row in full.
   *
   * @param tableName table to read
   * @param id value of the table's single key column, compared as text
   * @return columns and the row, or empty when the table, its key or the row is missing
   */
  public Optional<Map<String, Object>> row(String tableName, String id) throws Exception {
    if (!exists(tableName) || id == null) {
      return Optional.empty();
    }
    String key = primaryKey(tableName);
    if (key == null) {
      return Optional.empty();
    }
    List<Map<String, Object>> columns = columns(tableName);

    return database.query(
        dsl -> {
          Record found =
              dsl.select()
                  .from(table(name(tableName)))
                  .where(field(name(key)).cast(String.class).eq(id))
                  .limit(1)
                  .fetchOne();
          if (found == null) {
            return Optional.<Map<String, Object>>empty();
          }
          Map<String, Object> out = new LinkedHashMap<>();
          out.put("table", tableName);
          out.put("columns", columns);
          out.put("primaryKey", key);
          out.put("writable", writable(tableName, key));
          out.put("row", toMap(found, 0));
          return Optional.of(out);
        });
  }

  boolean exists(String tableName) throws Exception {
    return tableName != null
        && listTables().stream().anyMatch(t -> tableName.equals(t.get("name")));
  }

  List<Map<String, Object>> columns(String tableName) throws Exception {
    return database.query(
        dsl ->
            dsl.fetch(COLUMNS_SQL, tableName).stream()
                .map(
                    r -> {
                      Map<String, Object> m = new LinkedHashMap<>();
                      m.put("name", r.get(0, String.class));
                      m.put("type", r.get(1, String.class));
                      return m;
                    })
                .toList());
  }

  /** Returns the key column, or null when the table has none or a composite key. */
  String primaryKey(String tableName) throws Exception {
    List<String> keys =
        database.query(
            dsl -> new ArrayList<>(dsl.fetch(KEY_SQL, tableName).getValues(0, String.class)));
    return keys.size() == 1 ? keys.get(0) : null;
  }

  /**
   * Turns a row into a map of display values.
   *
   * @param record the row
   * @param maxText longest text to keep; 0 keeps all of it
   */
  private static Map<String, Object> toMap(Record record, int maxText) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (var field : record.fields()) {
      out.put(field.getName(), display(record.get(field), maxText));
    }
    return out;
  }

  private static Object display(Object value, int maxText) {
    if (value == null || value instanceof Number || value instanceof Boolean) {
      return value;
    }
    if (value instanceof byte[] bytes) {
      return "<binary " + bytes.length + " bytes>";
    }
    String text = String.valueOf(value);
    return maxText > 0 && text.length() > maxText ? text.substring(0, maxText) + "…" : text;
  }
}
