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
  public String getJarPath() {
    return "org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar";
  }

  @Override
  public String getDriverClass() {
    return "org.postgresql.Driver";
  }

  @Override
  public String buildUrl(Map<String, String> props) {
    return props.get("url");
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
}
