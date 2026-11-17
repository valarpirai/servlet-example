package com.example.datasource;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class PostgreSqlStrategy implements DataSourceStrategy {

  @Override
  public String getDbType() {
    return "postgresql";
  }

  @Override
  public String getDisplayName() {
    return "PostgreSQL";
  }

  @Override
  public String getJarPath() {
    return "org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar";
  }

  @Override
  public String getDriverClass() {
    return "org.postgresql.Driver";
  }

  @Override
  public String buildUrl(Map<String, String> props) {
    String url = props.get("url");
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("PostgreSQL 'url' property is required");
    }
    return url;
  }

  @Override
  public Properties buildConnectionProperties(
      String user, String password, Map<String, String> props) {
    Properties p = new Properties();
    p.setProperty("user", user);
    p.setProperty("password", password);
    return p;
  }

  @Override
  public Set<String> getSystemSchemas() {
    return Set.of("information_schema", "pg_catalog", "pg_toast", "pg_temp_1", "pg_toast_temp_1");
  }

  /**
   * PostgreSQL binds a connection to one database for its lifetime; switching requires a reconnect.
   */
  @Override
  public boolean supportsCatalogSwitch() {
    return false;
  }

  /**
   * Replaces the database path segment of the original {@code jdbc:postgresql://host:port/db} URL,
   * preserving any query string. If the original URL has no path segment at all, one is appended.
   */
  @Override
  public String buildUrlForDatabase(Map<String, String> originalProps, String database) {
    String url = originalProps.get("url");
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException(
          "Cannot switch database: original connection has no 'url' property");
    }
    int authorityStart = url.indexOf("//");
    if (authorityStart < 0) {
      throw new IllegalArgumentException("Malformed PostgreSQL URL: " + url);
    }
    int pathStart = url.indexOf('/', authorityStart + 2);
    if (pathStart < 0) {
      return url + "/" + database;
    }
    int queryStart = url.indexOf('?', pathStart);
    String prefix = url.substring(0, pathStart + 1);
    String suffix = queryStart >= 0 ? url.substring(queryStart) : "";
    return prefix + database + suffix;
  }
}
