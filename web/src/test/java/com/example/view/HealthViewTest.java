package com.example.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthViewTest {

  @Test
  void bodyIsByteIdenticalToTheOldFormat() {
    assertEquals(
        "{\"status\":\"UP\",\"timestamp\":1700000000000,\"uptime\":\"12 ms\"}",
        HealthView.json("UP", 1700000000000L, 12));
  }

  @Test
  void downStatusIsKept() {
    assertEquals(
        "{\"status\":\"DOWN\",\"timestamp\":5,\"uptime\":\"0 ms\"}", HealthView.json("DOWN", 5, 0));
  }
}
