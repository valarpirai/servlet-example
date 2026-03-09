package com.example.datasource;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class MySqlStrategy implements DataSourceStrategy {

  @Override
  public String getDbType() {
    return "mysql";
  }

  @Override
  public String getJarPath() {
    return "com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar";
  }

  @Override
  public String getDriverClass() {
    return "com.mysql.cj.jdbc.Driver";
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
    return Set.of("information_schema", "performance_schema", "sys", "mysql");
  }
}
