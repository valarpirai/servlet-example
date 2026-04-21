package com.example.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DbConfigTest {

  @BeforeEach
  void setUp() {
    // Ensure YAML-only mode (no DB repository)
    com.example.servlet.util.PropertiesUtil.setPropertyRepository(null);
  }

  @Test
  void fromProperties_returnsConfig_withDefaultYamlValues() {
    DbConfig config = DbConfig.fromProperties();
    assertNotNull(config);
    assertNotNull(config.getUrl());
    assertNotNull(config.getUsername());
    assertNotNull(config.getPassword());
  }

  @Test
  void fromProperties_urlContainsJdbc() {
    DbConfig config = DbConfig.fromProperties();
    assertNotNull(config);
    assertTrue(config.getUrl().startsWith("jdbc:"), "URL should be a JDBC URL");
  }

  @Test
  void getters_returnConstructedValues() throws Exception {
    // DbConfig constructor is private — go through fromProperties()
    DbConfig config = DbConfig.fromProperties();
    assertNotNull(config);
    assertFalse(config.getUrl().isBlank());
    assertFalse(config.getUsername().isBlank());
    assertFalse(config.getPassword().isBlank());
  }
}
