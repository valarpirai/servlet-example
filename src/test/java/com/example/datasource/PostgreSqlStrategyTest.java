package com.example.datasource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostgreSqlStrategyTest {

  private final PostgreSqlStrategy strategy = new PostgreSqlStrategy();

  @Test
  void getDbType_returnsPostgresql() {
    assertEquals("postgresql", strategy.getDbType());
  }

  @Test
  void buildUrl_returnsUrlFromProps() {
    String url = strategy.buildUrl(Map.of("url", "jdbc:postgresql://localhost:5432/mydb"));
    assertEquals("jdbc:postgresql://localhost:5432/mydb", url);
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
    Properties p = strategy.buildConnectionProperties("alice", "secret", Map.of());
    assertEquals("alice", p.getProperty("user"));
    assertEquals("secret", p.getProperty("password"));
  }

  @Test
  void buildConnectionProperties_noExtraProperties() {
    Properties p = strategy.buildConnectionProperties("alice", "secret", Map.of("url", "jdbc:..."));
    assertEquals(2, p.size());
  }

  @Test
  void getSystemSchemas_containsExpectedSchemas() {
    assertEquals(
        Set.of("information_schema", "pg_catalog", "pg_toast", "pg_temp_1", "pg_toast_temp_1"),
        strategy.getSystemSchemas());
  }
}
