package com.example.view;

import com.example.util.JsonUtil;
import java.util.Map;

/** The body of the {@code /metrics} endpoint. */
public final class MetricsView {

  private MetricsView() {}

  /**
   * Renders the metrics body.
   *
   * @param snapshot the runtime metrics
   * @return the JSON text, with the snapshot under {@code metrics}
   */
  public static String json(Object snapshot) {
    return JsonUtil.toJson(Map.of("metrics", snapshot));
  }
}
