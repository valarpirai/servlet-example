package com.example.datasource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MySqlStrategyTest {

  private final MySqlStrategy strategy = new MySqlStrategy();

  @Test
  void getDbType_returnsMysql() {
    assertEquals("mysql", strategy.getDbType());
  }

  @Test
  void buildUrl_returnsUrlFromProps() {
    String url = strategy.buildUrl(Map.of("url", "jdbc:mysql://localhost:3306/mydb"));
    assertEquals("jdbc:mysql://localhost:3306/mydb", url);
  }

  @Test
  void buildUrl_missingUrl_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> strategy.buildUrl(Map.of()));
  }

  @Test
  void buildUrl_blankUrl_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> strategy.buildUrl(Map.of("url", "  ")));
  }

  @Test
  void buildConnectionProperties_containsUserAndPassword() {
    Properties p = strategy.buildConnectionProperties("bob", "pass", Map.of());
    assertEquals("bob", p.getProperty("user"));
    assertEquals("pass", p.getProperty("password"));
  }

  @Test
  void buildConnectionProperties_noExtraProperties() {
    Properties p = strategy.buildConnectionProperties("bob", "pass", Map.of("url", "jdbc:..."));
    assertEquals(2, p.size());
  }

  @Test
  void getSystemSchemas_containsExpectedSchemas() {
    assertEquals(
        Set.of("information_schema", "performance_schema", "sys", "mysql"),
        strategy.getSystemSchemas());
  }
}
