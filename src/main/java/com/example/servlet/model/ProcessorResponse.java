package com.example.servlet.model;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** Response from a request processor containing status, content type, body, and headers. */
@Getter
@Builder
public class ProcessorResponse {
  @Builder.Default private final int statusCode = 200;
  @Builder.Default private final String contentType = "application/json";
  @Builder.Default private final String body = "";
  @Builder.Default private final Map<String, String> headers = new HashMap<>();
}
