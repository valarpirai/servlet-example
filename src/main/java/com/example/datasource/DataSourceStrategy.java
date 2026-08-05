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

  /** Human-readable name shown in the UI's database-type dropdown, e.g. "PostgreSQL". */
  String getDisplayName();

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

  /**
   * True if the active database can be changed on an existing connection via {@link
   * java.sql.Connection#setCatalog}. False if the driver binds a connection to one database for its
   * lifetime, meaning a database switch requires reconnecting (see {@link #buildUrlForDatabase}).
   * True for all strategies by default.
   */
  default boolean supportsCatalogSwitch() {
    return true;
  }

  /**
   * Rebuilds the connection URL for a different database, given the original connect-time
   * properties (as passed to {@link #buildUrl}). Only called when {@link #supportsCatalogSwitch()}
   * is false.
   */
  default String buildUrlForDatabase(Map<String, String> originalProps, String database) {
    throw new UnsupportedOperationException(
        getDbType() + " supports in-connection catalog switching; buildUrlForDatabase is unused");
  }
}
