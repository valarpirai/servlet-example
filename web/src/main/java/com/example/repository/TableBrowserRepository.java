package com.example.repository;

import com.example.config.Database;
import com.example.record.TableRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads table structure and rows for the table browser. Rows come through {@link TableRecord}, so
 * the {@code record.allowedTables} setting limits which tables it can read. Table and column names
 * are checked by {@link TableRecord}; the caller still matches them against {@link #listTables()}.
 */
public class TableBrowserRepository {

  private final Database database;

  /**
   * Creates a repository.
   *
   * @param database database to read
   */
  public TableBrowserRepository(Database database) {
    this.database = database;
  }

  /**
   * Lists every base table.
   *
   * @return one map per table with {@code name} and {@code columns} (the column count)
   * @throws Exception when the query fails
   */
  public List<Map<String, Object>> listTables() throws Exception {
    return database.tables();
  }

  /**
   * Lists the columns of a table in order.
   *
   * @param tableName table to describe
   * @return one map per column with {@code name} and {@code type}
   * @throws Exception when the query fails
   */
  public List<Map<String, Object>> columns(String tableName) throws Exception {
    return database.columns(tableName);
  }

  /**
   * Finds the key column.
   *
   * @param tableName table to describe
   * @return the key column, or null when the table has none or a composite key
   */
  public String primaryKey(String tableName) {
    return database.primaryKey(tableName).orElse(null);
  }

  /**
   * Reads one page of rows with their raw values.
   *
   * @param tableName table to read
   * @param sort column to sort by, or null
   * @param descending true to sort the {@code sort} column high to low
   * @param key key column used to break ties, or null
   * @param limit rows to return
   * @param offset rows to skip
   * @return one map per row, column name to value
   * @throws IllegalArgumentException when the table is not allowed or a name is not valid
   */
  public List<Map<String, Object>> page(
      String tableName, String sort, boolean descending, String key, int limit, int offset) {
    TableRecord records = new TableRecord(database, tableName);
    if (sort != null) {
      if (descending) {
        records.orderByDesc(sort);
      } else {
        records.orderBy(sort);
      }
    }
    // The key breaks ties, so a row never lands on two pages.
    if (key != null && !key.equals(sort)) {
      records.orderBy(key);
    }
    records.setLimit(limit);
    records.setOffset(offset);
    records.query();
    return rowsOf(records);
  }

  /**
   * Counts the rows of a table.
   *
   * @param tableName table to count
   * @return the row count
   */
  public long count(String tableName) {
    return new TableRecord(database, tableName).count();
  }

  /**
   * Reads one row by key.
   *
   * @param tableName table to read
   * @param keyColumn key column
   * @param id key value, compared as text
   * @return the row with raw values, or empty when no row matches
   */
  public Optional<Map<String, Object>> findByKey(String tableName, String keyColumn, String id) {
    TableRecord records = new TableRecord(database, tableName);
    records.addQueryAsText(keyColumn, id);
    records.setLimit(1);
    records.query();
    return rowsOf(records).stream().findFirst();
  }

  private static List<Map<String, Object>> rowsOf(TableRecord records) {
    List<Map<String, Object>> rows = new java.util.ArrayList<>();
    while (records.next()) {
      Map<String, Object> row = new LinkedHashMap<>();
      for (String column : records.getColumnNames()) {
        row.put(column, records.getTypedValue(column));
      }
      rows.add(row);
    }
    return rows;
  }
}
