package com.example.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/**
 * Thin wrapper around jOOQ that manages connection lifecycle and transactions. All DB access goes
 * through {@link #query} or {@link #transact}; raw connections are only for streaming.
 */
public class Database {

  /** Functional interface for database work that may throw any checked exception. */
  @FunctionalInterface
  public interface Work<T> {
    T run(DSLContext dsl) throws Exception;
  }

  private static final String KEY_SQL =
      "select kcu.column_name from information_schema.table_constraints tc"
          + " join information_schema.key_column_usage kcu"
          + " on kcu.constraint_name = tc.constraint_name"
          + " and kcu.constraint_schema = tc.constraint_schema"
          + " where tc.constraint_type = 'PRIMARY KEY'"
          + " and tc.table_schema = current_schema() and tc.table_name = ?";

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

  private final DbConfig config;
  private final ConcurrentHashMap<String, Optional<String>> primaryKeys = new ConcurrentHashMap<>();

  public Database(DbConfig config) {
    this.config = config;
  }

  /**
   * Run a single query or DML statement on a fresh connection. The connection is closed when the
   * work completes (or throws).
   */
  public <T> T query(Work<T> fn) throws Exception {
    try (Connection c = config.getConnection()) {
      return fn.run(DSL.using(c, SQLDialect.POSTGRES));
    }
  }

  /**
   * Run work inside a transaction. Commits on success, rolls back on any exception, then re-throws.
   */
  public <T> T transact(Work<T> fn) throws Exception {
    try (Connection c = config.getConnection()) {
      c.setAutoCommit(false);
      try {
        T result = fn.run(DSL.using(c, SQLDialect.POSTGRES));
        c.commit();
        return result;
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }

  /**
   * Open a raw JDBC connection for cursor-based streaming. Caller is responsible for closing it.
   */
  public Connection openConnection() throws SQLException {
    return config.getConnection();
  }

  /** Connection pool load, or null before the pool starts. */
  public java.util.Map<String, Object> poolStats() {
    return config == null ? null : config.poolStats();
  }

  /**
   * Finds the primary key column of a table. The answer is cached per table.
   *
   * @param table table name
   * @return the key column, or empty when the table has no primary key or a composite one
   * @throws IllegalStateException when the lookup fails; failures are not cached
   */
  public Optional<String> primaryKey(String table) {
    Optional<String> cached = primaryKeys.get(table);
    if (cached != null) {
      return cached;
    }
    try {
      List<String> columns = query(dsl -> dsl.fetch(KEY_SQL, table).getValues(0, String.class));
      Optional<String> key = columns.size() == 1 ? Optional.of(columns.get(0)) : Optional.empty();
      primaryKeys.put(table, key);
      return key;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Cannot read the primary key of " + table + ": " + e.getMessage(), e);
    }
  }

  /**
   * Lists every base table in the {@code public} schema.
   *
   * @return one map per table with {@code name} and {@code columns} (the column count)
   * @throws Exception when the query fails
   */
  public List<Map<String, Object>> tables() throws Exception {
    return query(
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
   * Lists the columns of a table in order.
   *
   * @param table table name
   * @return one map per column with {@code name} and {@code type}; empty for an unknown table
   * @throws Exception when the query fails
   */
  public List<Map<String, Object>> columns(String table) throws Exception {
    return query(
        dsl ->
            dsl.fetch(COLUMNS_SQL, table).stream()
                .map(
                    r -> {
                      Map<String, Object> m = new LinkedHashMap<>();
                      m.put("name", r.get(0, String.class));
                      m.put("type", r.get(1, String.class));
                      return m;
                    })
                .toList());
  }
}
