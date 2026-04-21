package com.example.config;

import java.sql.Connection;
import java.sql.SQLException;
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

  private final DbConfig config;

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
}
