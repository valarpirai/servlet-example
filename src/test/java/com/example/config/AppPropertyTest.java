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
  void gettersReturnConstructorValues() {
    Instant now = Instant.now();
    AppProperty p =
        new AppProperty(
            7L, "server.port", "9090", "INTEGER", "Port", true, now, now, "admin", "admin");

    assertEquals(7L, p.getId());
    assertEquals("server.port", p.getName());
    assertEquals("9090", p.getValue());
    assertEquals("INTEGER", p.getType());
    assertEquals("Port", p.getDescription());
    assertTrue(p.isActive());
    assertEquals(now, p.getCreatedAt());
    assertEquals(now, p.getUpdatedAt());
    assertEquals("admin", p.getCreatedBy());
    assertEquals("admin", p.getUpdatedBy());
  }

  @Test
  void nullValueAllowed() {
    AppProperty p = build(1L, "empty.prop", null, true);
    assertNull(p.getValue());
  }

  @Test
  void inactivePropertyReflectsFlag() {
    AppProperty p = build(2L, "disabled.prop", "x", false);
    assertFalse(p.isActive());
  }
}
