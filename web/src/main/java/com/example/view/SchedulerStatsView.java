package com.example.view;

import java.util.LinkedHashMap;
import java.util.Map;

/** The body of {@code GET /api/scheduler/stats}. Key order is part of the contract. */
public final class SchedulerStatsView {

  private SchedulerStatsView() {}

  /**
   * Builds the body for a node that runs no scheduler.
   *
   * @return {@code {"enabled": false}}
   */
  public static Map<String, Object> disabled() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("enabled", false);
    return body;
  }

  /**
   * Builds the body for a running scheduler.
   *
   * @param node this node's counters
   * @param table the job row counts by state
   * @return the body, with keys {@code enabled}, {@code node}, {@code table} in that order
   */
  public static Map<String, Object> enabled(Object node, Object table) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("enabled", true);
    body.put("node", node);
    body.put("table", table);
    return body;
  }

  /**
   * Builds the body for a running scheduler whose job table could not be counted.
   *
   * @param node this node's counters
   * @param message the failure message, or null
   * @return the body, with {@code table} set to {@code {"error": message}}
   */
  public static Map<String, Object> tableFailed(Object node, String message) {
    return enabled(node, Map.of("error", String.valueOf(message)));
  }
}
