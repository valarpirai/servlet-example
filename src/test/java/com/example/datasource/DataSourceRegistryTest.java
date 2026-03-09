package com.example.datasource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import org.junit.jupiter.api.Test;

class DataSourceRegistryTest {

  @Test
  void getInstance_returnsSameInstance() {
    assertSame(DataSourceRegistry.getInstance(), DataSourceRegistry.getInstance());
  }

  @Test
  void get_knownType_returnsStrategy() {
    assertNotNull(DataSourceRegistry.getInstance().get("postgresql"));
    assertNotNull(DataSourceRegistry.getInstance().get("mysql"));
    assertNotNull(DataSourceRegistry.getInstance().get("snowflake"));
  }

  @Test
  void get_caseInsensitive() {
    DataSourceStrategy s = DataSourceRegistry.getInstance().get("POSTGRESQL");
    assertNotNull(s);
    assertEquals("postgresql", s.getDbType());
  }

  @Test
  void get_unknownType_returnsNull() {
    assertNull(DataSourceRegistry.getInstance().get("oracle"));
  }

  @Test
  void get_null_returnsNull() {
    assertNull(DataSourceRegistry.getInstance().get(null));
  }

  @Test
  void isSupported_knownType_returnsTrue() {
    assertTrue(DataSourceRegistry.getInstance().isSupported("mysql"));
  }

  @Test
  void isSupported_unknownType_returnsFalse() {
    assertFalse(DataSourceRegistry.getInstance().isSupported("oracle"));
  }

  @Test
  void all_returnsThreeStrategies() {
    Collection<DataSourceStrategy> all = DataSourceRegistry.getInstance().all();
    assertEquals(3, all.size());
  }
}
