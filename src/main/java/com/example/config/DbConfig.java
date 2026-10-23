package com.example.config;

import com.example.util.PropertiesUtil;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads PostgreSQL connection credentials from application.yml and lends pooled connections. */
public class DbConfig {

  private static DbConfig instance;

  private final String url;
  private final String username;
  private final String password;
  private final int poolSize;
  private volatile HikariDataSource pool;

  private DbConfig(String url, String username, String password, int poolSize) {
    this.url = url;
    this.username = username;
    this.password = password;
    this.poolSize = poolSize;
  }

  /**
   * Build a DbConfig from the bootstrap application.yml. Returns null when any required key is
   * absent. Calls with the same settings return the same instance, so the JVM has one pool.
   */
  public static synchronized DbConfig fromProperties() {
    String url = PropertiesUtil.getString("db.url", null);
    String username = PropertiesUtil.getString("db.username", null);
    String password = PropertiesUtil.getString("db.password", null);
    if (url == null || username == null || password == null) {
      return null;
    }
    // Read now: inside pool() this lookup would reach the database, which needs the pool.
    int poolSize = PropertiesUtil.getInt("db.pool.size", 10);
    if (instance == null
        || !instance.url.equals(url)
        || !instance.username.equals(username)
        || !instance.password.equals(password)) {
      if (instance != null) {
        instance.close();
      }
      instance = new DbConfig(url, username, password, poolSize);
    }
    return instance;
  }

  /**
   * Borrow a connection from the pool. The pool starts on first use, so a missing database fails
   * here and not at startup. Closing the connection returns it to the pool.
   */
  public Connection getConnection() throws SQLException {
    return pool().getConnection();
  }

  private HikariDataSource pool() {
    HikariDataSource p = pool;
    if (p == null) {
      synchronized (this) {
        p = pool;
        if (p == null) {
          p = new HikariDataSource();
          p.setPoolName("app-db");
          p.setJdbcUrl(url);
          p.setUsername(username);
          p.setPassword(password);
          p.setMaximumPoolSize(poolSize);
          p.setConnectionTimeout(5000);
          pool = p;
        }
      }
    }
    return p;
  }

  /**
   * Current pool load.
   *
   * @return active, idle, total, waiting and max connections, or null before the pool starts
   */
  public Map<String, Object> poolStats() {
    HikariDataSource p = pool;
    if (p == null || p.getHikariPoolMXBean() == null) {
      return null;
    }
    HikariPoolMXBean bean = p.getHikariPoolMXBean();
    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("active", bean.getActiveConnections());
    stats.put("idle", bean.getIdleConnections());
    stats.put("total", bean.getTotalConnections());
    stats.put("waiting", bean.getThreadsAwaitingConnection());
    stats.put("max", p.getMaximumPoolSize());
    return stats;
  }

  /** Close the pool. A later getConnection() starts a fresh one. */
  public synchronized void close() {
    if (pool != null) {
      pool.close();
      pool = null;
    }
  }

  public String getUrl() {
    return url;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
