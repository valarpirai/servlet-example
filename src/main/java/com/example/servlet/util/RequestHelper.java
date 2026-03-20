package com.example.servlet.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Helper class for common request handling patterns to reduce code duplication. */
public class RequestHelper {

  /**
   * Reads the entire request body as a string.
   *
   * @param request HTTP request
   * @return Request body as string
   * @throws IOException if reading fails
   */
  public static String readRequestBody(HttpServletRequest request) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = request.getReader()) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
    }
    return sb.toString();
  }

  /**
   * Reads and parses request body as JSON.
   *
   * @param request HTTP request
   * @return Parsed JsonObject
   * @throws IOException if reading fails
   * @throws JsonSyntaxException if JSON is invalid
   * @throws IllegalArgumentException if body is empty
   */
  public static JsonObject readJsonBody(HttpServletRequest request) throws IOException {
    String body = readRequestBody(request);
    if (isEmptyBody(body)) {
      throw new IllegalArgumentException("Empty request body");
    }
    return JsonParser.parseString(body).getAsJsonObject();
  }

  /**
   * Converts JsonObject to Map recursively.
   *
   * @param jsonObject JSON object to convert
   * @return Map representation
   */
  public static Map<String, Object> jsonObjectToMap(JsonObject jsonObject) {
    Map<String, Object> map = new HashMap<>();

    jsonObject
        .entrySet()
        .forEach(
            entry -> {
              String key = entry.getKey();
              com.google.gson.JsonElement value = entry.getValue();

              if (value.isJsonNull()) {
                map.put(key, null);
              } else if (value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isNumber()) {
                  map.put(key, value.getAsNumber());
                } else if (value.getAsJsonPrimitive().isBoolean()) {
                  map.put(key, value.getAsBoolean());
                } else {
                  map.put(key, value.getAsString());
                }
              } else if (value.isJsonObject()) {
                map.put(key, jsonObjectToMap(value.getAsJsonObject()));
              } else if (value.isJsonArray()) {
                java.util.List<Object> list = new java.util.ArrayList<>();
                value
                    .getAsJsonArray()
                    .forEach(
                        element -> {
                          if (element.isJsonObject()) {
                            list.add(jsonObjectToMap(element.getAsJsonObject()));
                          } else if (element.isJsonPrimitive()) {
                            list.add(element.getAsString());
                          }
                        });
                map.put(key, list);
              }
            });

    return map;
  }

  /**
   * Checks if request body is empty or null.
   *
   * @param requestBody Request body string
   * @return true if empty or null
   */
  public static boolean isEmptyBody(String requestBody) {
    return requestBody == null || requestBody.trim().isEmpty();
  }
}
