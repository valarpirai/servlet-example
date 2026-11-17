package com.example.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class DbPropertiesLoaderTest {

  @Test
  void isDatabaseUpIsFalseWhenNoDatabaseIsConfigured() {
    assertFalse(DbPropertiesLoader.isDatabaseUp());
  }
}
