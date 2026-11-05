package com.example.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AllowedTablesTest {

  @Test
  void blankPermitsEverything() {
    assertTrue(AllowedTables.parse("").permits("anything"));
    assertTrue(AllowedTables.parse(null).permits("anything"));
    assertTrue(AllowedTables.parse("  ").permits("anything"));
  }

  @Test
  void listedTablesOnly() {
    AllowedTables allowed = AllowedTables.parse("a, b:id");
    assertTrue(allowed.permits("a"));
    assertTrue(allowed.permits("b"));
    assertFalse(allowed.permits("c"));
  }

  @Test
  void keyFromPair() {
    AllowedTables allowed = AllowedTables.parse("a, b:id");
    assertEquals(Optional.of("id"), allowed.keyFor("b"));
    assertEquals(Optional.empty(), allowed.keyFor("a"));
  }

  @Test
  void emptyEntriesSkipped() {
    AllowedTables allowed = AllowedTables.parse("a,,");
    assertTrue(allowed.permits("a"));
    assertFalse(allowed.permits(""));
  }
}
