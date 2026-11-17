package com.example.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchedulerStatsViewTest {

  @Test
  void disabledHasOnlyEnabledFalse() {
    assertEquals(Map.of("enabled", false), SchedulerStatsView.disabled());
  }

  @Test
  void enabledKeepsKeyOrder() {
    Map<String, Object> body = SchedulerStatsView.enabled(Map.of("ran", 1), Map.of("ready", 2));

    assertEquals(List.of("enabled", "node", "table"), List.copyOf(body.keySet()));
    assertEquals(true, body.get("enabled"));
    assertEquals(Map.of("ran", 1), body.get("node"));
    assertEquals(Map.of("ready", 2), body.get("table"));
  }

  @Test
  void tableFailureCarriesTheMessage() {
    Map<String, Object> body = SchedulerStatsView.tableFailed(Map.of("ran", 1), "boom");

    assertEquals(List.of("enabled", "node", "table"), List.copyOf(body.keySet()));
    assertEquals(Map.of("error", "boom"), body.get("table"));
  }

  @Test
  void nullMessageBecomesTheTextNull() {
    Map<String, Object> body = SchedulerStatsView.tableFailed(Map.of(), null);

    assertEquals(Map.of("error", "null"), body.get("table"));
  }
}
