package com.example.datasource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SnowflakeStrategyTest {

  private final SnowflakeStrategy strategy = new SnowflakeStrategy();

  @Test
  void getDbType_returnsSnowflake() {
    assertEquals("snowflake", strategy.getDbType());
  }

  @Test
  void buildUrl_assemblesFromAccount() {
    String url = strategy.buildUrl(Map.of("account", "myorg-myaccount"));
    assertEquals("jdbc:snowflake://myorg-myaccount.snowflakecomputing.com/", url);
  }

  @Test
  void buildUrl_missingAccount_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> strategy.buildUrl(Map.of()));
  }

  @Test
  void buildUrl_blankAccount_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> strategy.buildUrl(Map.of("account", "  ")));
  }

  @Test
  void buildConnectionProperties_containsUserAndPassword() {
    Properties p = strategy.buildConnectionProperties("alice", "secret", Map.of("account", "a"));
    assertEquals("alice", p.getProperty("user"));
    assertEquals("secret", p.getProperty("password"));
  }

  @Test
  void buildConnectionProperties_allOptionalFields_included() {
    Map<String, String> props = new HashMap<>();
    props.put("account", "myorg");
    props.put("warehouse", "COMPUTE_WH");
    props.put("database", "MYDB");
    props.put("schema", "PUBLIC");
    props.put("role", "SYSADMIN");

    Properties p = strategy.buildConnectionProperties("u", "p", props);

    assertEquals("COMPUTE_WH", p.getProperty("warehouse"));
    assertEquals("MYDB", p.getProperty("db"));
    assertEquals("PUBLIC", p.getProperty("schema"));
    assertEquals("SYSADMIN", p.getProperty("role"));
  }

  @Test
  void buildConnectionProperties_blankOptionalFields_skipped() {
    Map<String, String> props = new HashMap<>();
    props.put("account", "myorg");
    props.put("warehouse", "  ");
    props.put("database", "");

    Properties p = strategy.buildConnectionProperties("u", "p", props);

    assertNull(p.getProperty("warehouse"));
    assertNull(p.getProperty("db"));
  }

  @Test
  void buildConnectionProperties_absentOptionalFields_skipped() {
    Properties p = strategy.buildConnectionProperties("u", "p", Map.of("account", "myorg"));
    assertEquals(2, p.size());
  }

  @Test
  void getSystemSchemas_isExactlyInformationSchema() {
    assertEquals(Set.of("information_schema"), strategy.getSystemSchemas());
  }
}
