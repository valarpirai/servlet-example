package com.example.tableapi;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.record.AllowedTables;
import com.example.repository.TableBrowserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

  private final TableBrowserRepository repository;

  public TableApi(Database database) {
    this.repository = new TableBrowserRepository(database);
  }

  /**
   * Opens the browser on the application database.
   *
   * @return the browser, or empty when no database is configured
   */
  public static Optional<TableApi> forApplicationDatabase() {
    return Optional.ofNullable(DbPropertiesLoader.getDatabase()).map(TableApi::new);
  }

  /**
   * Lists every table.
   *
   * @return one map per table with {@code name} and {@code columns} (the column count)
   */
  public List<Map<String, Object>> listTables() throws Exception {
    return repository.listTables().stream()
        .filter(t -> AllowedTables.current().usable((String) t.get("name")))
        .toList();
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

    List<Map<String, Object>> rows =
        repository
            .page(tableName, sort, descending, key, pageSize, (pageNumber - 1) * pageSize)
            .stream()
            .map(r -> display(r, LIST_CELL_MAX))
            .toList();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("table", tableName);
    out.put("columns", columns);
    out.put("primaryKey", key);
    out.put("writable", writable(tableName, key));
    out.put("total", repository.count(tableName));
    out.put("page", pageNumber);
    out.put("size", pageSize);
    out.put("sort", sort);
    out.put("descending", sort != null && descending);
    out.put("rows", rows);
    return Optional.of(out);
  }

  /** A table is writable when it is permitted and any key the setting lists is the database's. */
  static boolean writable(String tableName, String primaryKey) {
    AllowedTables allowed = AllowedTables.current();
    return primaryKey != null
        && allowed.permits(tableName)
        && allowed.keyFor(tableName).map(primaryKey::equals).orElse(true);
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

    return repository
        .findByKey(tableName, key, id)
        .map(
            found -> {
              Map<String, Object> out = new LinkedHashMap<>();
              out.put("table", tableName);
              out.put("columns", columns);
              out.put("primaryKey", key);
              out.put("writable", writable(tableName, key));
              out.put("row", display(found, 0));
              return out;
            });
  }

  boolean exists(String tableName) throws Exception {
    return tableName != null
        && listTables().stream().anyMatch(t -> tableName.equals(t.get("name")));
  }

  List<Map<String, Object>> columns(String tableName) throws Exception {
    return repository.columns(tableName);
  }

  /** Returns the key column, or null when the table has none or a composite key. */
  String primaryKey(String tableName) throws Exception {
    return repository.primaryKey(tableName);
  }

  /**
   * Turns a row of raw values into display values.
   *
   * @param row column name to raw value
   * @param maxText longest text to keep; 0 keeps all of it
   */
  private static Map<String, Object> display(Map<String, Object> row, int maxText) {
    Map<String, Object> out = new LinkedHashMap<>();
    row.forEach((column, value) -> out.put(column, display(value, maxText)));
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
