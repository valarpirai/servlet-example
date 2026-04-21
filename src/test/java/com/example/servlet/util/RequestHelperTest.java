package com.example.servlet.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestHelperTest {

  @Mock private HttpServletRequest request;

  private void givenBody(String body) throws IOException {
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
  }

  // --- readRequestBody ---

  @Test
  void readRequestBody_returnsBodyText() throws IOException {
    givenBody("hello world");
    assertEquals("hello world", RequestHelper.readRequestBody(request));
  }

  @Test
  void readRequestBody_returnsEmptyStringForEmptyBody() throws IOException {
    givenBody("");
    assertEquals("", RequestHelper.readRequestBody(request));
  }

  @Test
  void readRequestBody_concatenatesMultipleLines() throws IOException {
    givenBody("line1\nline2");
    assertEquals("line1line2", RequestHelper.readRequestBody(request));
  }

  // --- readJsonBody ---

  @Test
  void readJsonBody_parsesValidJson() throws IOException {
    givenBody("{\"key\":\"value\"}");
    JsonObject obj = RequestHelper.readJsonBody(request);
    assertEquals("value", obj.get("key").getAsString());
  }

  @Test
  void readJsonBody_throwsOnEmptyBody() throws IOException {
    givenBody("");
    assertThrows(IllegalArgumentException.class, () -> RequestHelper.readJsonBody(request));
  }

  @Test
  void readJsonBody_throwsOnBlankBody() throws IOException {
    givenBody("   ");
    assertThrows(IllegalArgumentException.class, () -> RequestHelper.readJsonBody(request));
  }

  @Test
  void readJsonBody_throwsOnInvalidJson() throws IOException {
    givenBody("not json");
    assertThrows(JsonSyntaxException.class, () -> RequestHelper.readJsonBody(request));
  }

  // --- isEmptyBody ---

  @Test
  void isEmptyBody_trueForNull() {
    assertTrue(RequestHelper.isEmptyBody(null));
  }

  @Test
  void isEmptyBody_trueForEmpty() {
    assertTrue(RequestHelper.isEmptyBody(""));
  }

  @Test
  void isEmptyBody_trueForBlank() {
    assertTrue(RequestHelper.isEmptyBody("   "));
  }

  @Test
  void isEmptyBody_falseForContent() {
    assertFalse(RequestHelper.isEmptyBody("{}"));
  }

  // --- jsonObjectToMap ---

  @Test
  void jsonObjectToMap_convertsStringValue() {
    JsonObject json = new JsonObject();
    json.addProperty("name", "Alice");
    Map<String, Object> map = RequestHelper.jsonObjectToMap(json);
    assertEquals("Alice", map.get("name"));
  }

  @Test
  void jsonObjectToMap_convertsNumberValue() {
    JsonObject json = new JsonObject();
    json.addProperty("age", 30);
    Map<String, Object> map = RequestHelper.jsonObjectToMap(json);
    assertEquals(30, ((Number) map.get("age")).intValue());
  }

  @Test
  void jsonObjectToMap_convertsBooleanValue() {
    JsonObject json = new JsonObject();
    json.addProperty("active", true);
    Map<String, Object> map = RequestHelper.jsonObjectToMap(json);
    assertEquals(true, map.get("active"));
  }

  @Test
  void jsonObjectToMap_convertsNullValue() {
    JsonObject json = new JsonObject();
    json.add("empty", com.google.gson.JsonNull.INSTANCE);
    Map<String, Object> map = RequestHelper.jsonObjectToMap(json);
    assertNull(map.get("empty"));
  }

  @Test
  void jsonObjectToMap_convertsNestedObject() {
    JsonObject inner = new JsonObject();
    inner.addProperty("city", "SF");
    JsonObject outer = new JsonObject();
    outer.add("address", inner);
    Map<String, Object> map = RequestHelper.jsonObjectToMap(outer);
    @SuppressWarnings("unchecked")
    Map<String, Object> address = (Map<String, Object>) map.get("address");
    assertEquals("SF", address.get("city"));
  }

  @Test
  void jsonObjectToMap_convertsArrayValue() {
    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
    arr.add("a");
    arr.add("b");
    JsonObject json = new JsonObject();
    json.add("items", arr);
    Map<String, Object> map = RequestHelper.jsonObjectToMap(json);
    @SuppressWarnings("unchecked")
    List<Object> items = (List<Object>) map.get("items");
    assertEquals(List.of("a", "b"), items);
  }
}
