package com.example.datasource;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Encapsulates all per-database behaviour: driver location, URL construction, connection
 * properties, and which schemas are system-internal.
 *
 * <p>Register implementations in {@link DataSourceRegistry}.
 */
public interface DataSourceStrategy {

  /** Short lowercase key used in API requests: "mysql", "postgresql", "snowflake". */
  String getDbType();

  /** Maven Central relative JAR path, e.g. "org/postgresql/postgresql/42.7.3/...jar". */
  String getJarPath();

  /** Fully-qualified JDBC driver class name. */
  String getDriverClass();

  /**
   * Build the JDBC URL from the user-supplied connection properties.
   *
   * <p>For PostgreSQL/MySQL the caller passes {@code url} directly. For Snowflake the URL is
   * assembled from {@code account}.
   */
  String buildUrl(Map<String, String> props);

  /**
   * Build the {@link Properties} object passed to {@link java.sql.DriverManager#getConnection}.
   * Must always include {@code user} and {@code password}.
   */
  Properties buildConnectionProperties(String user, String password, Map<String, String> props);

  /**
   * Schema names that should be hidden from the table browser (system/internal schemas). Compared
   * case-insensitively.
   */
  Set<String> getSystemSchemas();
}
