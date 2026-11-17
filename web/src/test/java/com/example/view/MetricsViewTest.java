package com.example.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.util.JsonUtil;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricsViewTest {

  @Test
  void wrapsTheSnapshotUnderMetrics() {
    Map<String, Object> snapshot = Map.of("requests", 3);

    assertEquals(JsonUtil.toJson(Map.of("metrics", snapshot)), MetricsView.json(snapshot));
  }
}
