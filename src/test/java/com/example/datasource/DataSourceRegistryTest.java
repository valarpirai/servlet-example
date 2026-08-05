package com.example.datasource;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
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

  /**
   * Regression test: under embedded Tomcat, request threads run with a WebappClassLoader as their
   * context classloader, distinct from the one that loaded DataSourceRegistry. Discovery must not
   * depend on the thread's context classloader, or it silently finds zero strategies in that
   * environment despite passing here.
   */
  @Test
  void discovery_doesNotDependOnThreadContextClassLoader() throws Exception {
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0], null));
    try {
      Constructor<DataSourceRegistry> ctor = DataSourceRegistry.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      DataSourceRegistry registry = ctor.newInstance();
      assertEquals(3, registry.all().size());
    } finally {
      Thread.currentThread().setContextClassLoader(original);
    }
  }
}
