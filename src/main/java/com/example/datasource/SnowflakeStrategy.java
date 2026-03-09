package com.example.datasource;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class SnowflakeStrategy implements DataSourceStrategy {

  @Override
  public String getDbType() {
    return "snowflake";
  }

  @Override
  public String getJarPath() {
    return "net/snowflake/snowflake-jdbc/3.15.0/snowflake-jdbc-3.15.0.jar";
  }

  @Override
  public String getDriverClass() {
    return "net.snowflake.client.jdbc.SnowflakeDriver";
  }

  /** Assembles the Snowflake JDBC URL from the {@code account} property. */
  @Override
  public String buildUrl(Map<String, String> props) {
    return "jdbc:snowflake://" + props.get("account") + ".snowflakecomputing.com/";
  }

  /** Adds optional Snowflake-specific connection properties: warehouse, db, schema, role. */
  @Override
  public Properties buildConnectionProperties(
      String user, String password, Map<String, String> props) {
    Properties p = new Properties();
    p.setProperty("user", user);
    p.setProperty("password", password);
    addIfPresent(p, props, "warehouse");
    addIfPresent(p, props, "database", "db");
    addIfPresent(p, props, "schema");
    addIfPresent(p, props, "role");
    return p;
  }

  private void addIfPresent(Properties p, Map<String, String> props, String key) {
    addIfPresent(p, props, key, key);
  }

  private void addIfPresent(
      Properties p, Map<String, String> props, String inputKey, String jdbcKey) {
    String val = props.get(inputKey);
    if (val != null && !val.isBlank()) p.setProperty(jdbcKey, val);
  }

  @Override
  public Set<String> getSystemSchemas() {
    return Set.of("information_schema");
  }
}
