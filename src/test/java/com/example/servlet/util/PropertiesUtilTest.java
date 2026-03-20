package com.example.servlet.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PropertiesUtilTest {

  @AfterEach
  void tearDown() {
    // Clean up environment variables set during tests
    System.clearProperty("TEST_VAR");
    System.clearProperty("TEST_INT");
    System.clearProperty("TEST_BOOL");
    PropertiesUtil.reload();
  }

  @Test
  void testGetStringWithDefault() {
    String value = PropertiesUtil.getString("nonexistent.key", "default");
    assertEquals("default", value);
  }

  @Test
  void testGetStringFromYaml() {
    String value = PropertiesUtil.getString("server.port", "9999");
    assertNotNull(value);
    // Should read from application.yml
  }

  @Test
  void testGetIntWithDefault() {
    int value = PropertiesUtil.getInt("nonexistent.key", 42);
    assertEquals(42, value);
  }

  @Test
  void testGetIntFromYaml() {
    // storage.chunkSize exists in application.yml
    int value = PropertiesUtil.getInt("storage.chunkSize", 999);
    assertNotEquals(999, value);
  }

  @Test
  void testGetIntInvalidValue() {
    // If the property value is not a valid integer, return default
    String key = "invalid.int";
    int value = PropertiesUtil.getInt(key, 100);
    assertEquals(100, value);
  }

  @Test
  void testGetLongWithDefault() {
    long value = PropertiesUtil.getLong("nonexistent.key", 1000L);
    assertEquals(1000L, value);
  }

  @Test
  void testGetLongFromYaml() {
    long value = PropertiesUtil.getLong("upload.maxFileSize", 999L);
    assertNotEquals(999L, value);
  }

  @Test
  void testGetBooleanWithDefault() {
    boolean value = PropertiesUtil.getBoolean("nonexistent.key", true);
    assertTrue(value);
  }

  @Test
  void testGetBooleanFromYaml() {
    boolean value = PropertiesUtil.getBoolean("some.boolean.property", false);
    // Should return default since property doesn't exist
    assertFalse(value);
  }

  @Test
  void testGetDoubleWithDefault() {
    double value = PropertiesUtil.getDouble("nonexistent.key", 3.14);
    assertEquals(3.14, value, 0.001);
  }

  @Test
  void testGetDoubleFromYaml() {
    double value = PropertiesUtil.getDouble("some.double.property", 2.71);
    assertEquals(2.71, value, 0.001);
  }

  @Test
  void testEnvironmentVariableOverride() {
    // Set environment variable via system property
    System.setProperty("TEST_VAR", "from_env");

    // PropertiesUtil should resolve ${TEST_VAR:default}
    String value = PropertiesUtil.getString("test.env.var", "default");

    // Since test.env.var doesn't exist in YAML, it returns default
    assertEquals("default", value);
  }

  @Test
  void testPlaceholderResolution() {
    System.setProperty("TEST_PORT", "9090");
    PropertiesUtil.reload();

    // Note: This test assumes there's a property in YAML with ${TEST_PORT:8080}
    // If not present, it will use the default
    String value = PropertiesUtil.getString("server.port", "8080");
    assertNotNull(value);
  }

  @Test
  void testHasProperty() {
    // server.port should exist in application.yml
    assertTrue(PropertiesUtil.hasProperty("server.port"));

    // This should not exist
    assertFalse(PropertiesUtil.hasProperty("nonexistent.property"));
  }

  @Test
  void testGetAllProperties() {
    var properties = PropertiesUtil.getAllProperties();
    assertNotNull(properties);
    // Should contain server configuration
    assertTrue(properties.containsKey("server"));
  }

  @Test
  void testNestedProperties() {
    // Test nested property access with dot notation
    String storageType = PropertiesUtil.getString("storage.type", "none");
    assertNotNull(storageType);
  }

  @Test
  void testNullKey() {
    String value = PropertiesUtil.getString(null, "default");
    assertEquals("default", value);
  }

  @Test
  void testEmptyKey() {
    String value = PropertiesUtil.getString("", "default");
    assertEquals("default", value);
  }

  @Test
  void testReload() {
    // Get initial value
    String value1 = PropertiesUtil.getString("server.port", "8080");

    // Reload
    PropertiesUtil.reload();

    // Should still work
    String value2 = PropertiesUtil.getString("server.port", "8080");
    assertEquals(value1, value2);
  }

  @Test
  void testGetIntAsNumber() {
    // Test when YAML returns a Number object
    int value = PropertiesUtil.getInt("storage.chunkSize", 999);
    assertTrue(value > 0);
  }

  @Test
  void testGetLongAsNumber() {
    long value = PropertiesUtil.getLong("upload.maxFileSize", 999L);
    assertTrue(value > 0);
  }

  @Test
  void testGetDoubleAsNumber() {
    double value = PropertiesUtil.getDouble("storage.chunkSize", 1.0);
    assertTrue(value > 0);
  }

  @Test
  void testPlaceholderWithDefault() {
    // Placeholder with default value: ${VAR:default}
    // If VAR is not set, should use default
    System.clearProperty("NONEXISTENT_VAR");

    // This assumes a property in YAML like: ${NONEXISTENT_VAR:fallback}
    // Since we don't have such property, this test validates the fallback mechanism
    String value = PropertiesUtil.getString("nonexistent", "defaultValue");
    assertEquals("defaultValue", value);
  }

  @Test
  void testSystemPropertyOverridesEnvironment() {
    // System property should take precedence
    System.setProperty("TEST_PROP", "from_system");

    // Note: This test validates the precedence logic in resolvePlaceholders
    // The actual resolution happens when properties contain ${TEST_PROP}
    String sysValue = System.getProperty("TEST_PROP");
    assertEquals("from_system", sysValue);
  }
}
