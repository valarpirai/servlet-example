package com.example.config;

import com.example.servlet.util.PropertiesUtil;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Reads PostgreSQL connection credentials from application.yml and opens JDBC connections. */
public class DbConfig {

  private final String url;
  private final String username;
  private final String password;

  private DbConfig(String url, String username, String password) {
    this.url = url;
    this.username = username;
    this.password = password;
  }

  /**
   * Build a DbConfig from the bootstrap application.yml. Returns null when any required key is
   * absent.
   */
  public static DbConfig fromProperties() {
    String url = PropertiesUtil.getString("db.url", null);
    String username = PropertiesUtil.getString("db.username", null);
    String password = PropertiesUtil.getString("db.password", null);
    if (url == null || username == null || password == null) {
      return null;
    }
    return new DbConfig(url, username, password);
  }

  /** Open a new JDBC connection. Caller is responsible for closing it. */
  public Connection getConnection() throws SQLException {
    return DriverManager.getConnection(url, username, password);
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
