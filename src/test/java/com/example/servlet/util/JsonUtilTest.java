package com.example.servlet.util;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonUtilTest {

  @Test
  void isValidJson_validObject_returnsTrue() {
    assertTrue(JsonUtil.isValidJson("{\"key\":\"value\"}"));
  }

  @Test
  void isValidJson_validArray_returnsTrue() {
    assertTrue(JsonUtil.isValidJson("[1,2,3]"));
  }

  @Test
  void isValidJson_invalidJson_returnsFalse() {
    assertFalse(JsonUtil.isValidJson("{not json}"));
  }

  @Test
  void successResponse_hasStatusSuccess() {
    String json = JsonUtil.successResponse(Map.of("key", "val"));
    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
  }

  @Test
  void successResponse_hasDataAndTimestamp() {
    String json = JsonUtil.successResponse("hello");
    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
    assertTrue(obj.has("data"));
    assertTrue(obj.has("timestamp"));
    assertTrue(obj.get("timestamp").getAsLong() > 0);
  }

  @Test
  void errorResponse_hasAllFields() {
    String json = JsonUtil.errorResponse("Not Found", "Resource missing", 404);
    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
    assertEquals("Not Found", obj.get("error").getAsString());
    assertEquals("Resource missing", obj.get("message").getAsString());
    assertEquals(404, obj.get("status").getAsInt());
    assertTrue(obj.has("timestamp"));
  }

  @Test
  void toJson_fromJson_roundtrip() {
    Map<String, String> original = Map.of("hello", "world");
    String json = JsonUtil.toJson(original);
    assertTrue(JsonUtil.isValidJson(json));
    assertTrue(json.contains("hello"));
    assertTrue(json.contains("world"));
  }
}
