package com.example.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AppPropertyTest {

  private AppProperty build(long id, String name, String value, boolean active) {
    return new AppProperty(
        id, name, value, "STRING", "desc", active, Instant.now(), Instant.now(), "sys", "sys");
  }

  @Test
  void accessorsReturnConstructorValues() {
    Instant now = Instant.now();
    AppProperty p =
        new AppProperty(
            7L, "server.port", "9090", "INTEGER", "Port", true, now, now, "admin", "admin");

    assertEquals(7L, p.id());
    assertEquals("server.port", p.name());
    assertEquals("9090", p.value());
    assertEquals("INTEGER", p.type());
    assertEquals("Port", p.description());
    assertTrue(p.active());
    assertEquals(now, p.createdAt());
    assertEquals(now, p.updatedAt());
    assertEquals("admin", p.createdBy());
    assertEquals("admin", p.updatedBy());
  }

  @Test
  void nullValueAllowed() {
    AppProperty p = build(1L, "empty.prop", null, true);
    assertNull(p.value());
  }

  @Test
  void inactivePropertyReflectsFlag() {
    AppProperty p = build(2L, "disabled.prop", "x", false);
    assertFalse(p.active());
  }
}
