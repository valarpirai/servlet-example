package com.example.view;

/** The body of the {@code /health} endpoint. */
public final class HealthView {

  private HealthView() {}

  /**
   * Renders the health body.
   *
   * @param status {@code UP} or {@code DOWN}
   * @param timestampMillis the time of the check, in epoch milliseconds
   * @param uptimeMillis how long the app has run, in milliseconds
   * @return the JSON text
   */
  public static String json(String status, long timestampMillis, long uptimeMillis) {
    return String.format(
        "{\"status\":\"%s\",\"timestamp\":%d,\"uptime\":\"%d ms\"}",
        status, timestampMillis, uptimeMillis);
  }
}
