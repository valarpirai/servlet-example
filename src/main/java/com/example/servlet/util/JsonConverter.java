package com.example.servlet.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for converting between Gson JsonElement and Java objects. Shared between
 * ScriptProcessor and ApiScriptProcessor.
 */
public class JsonConverter {

  /**
   * Convert Gson JsonElement to Java object (recursive).
   *
   * @param element JsonElement to convert
   * @return Java object (Map, Array, primitive, or null)
   */
  public static Object convertJsonElement(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return null;
    }

    if (element.isJsonPrimitive()) {
      var primitive = element.getAsJsonPrimitive();
      if (primitive.isNumber()) {
        return primitive.getAsNumber();
      } else if (primitive.isBoolean()) {
        return primitive.getAsBoolean();
      } else {
        return primitive.getAsString();
      }
    }

    if (element.isJsonArray()) {
      var array = element.getAsJsonArray();
      Object[] result = new Object[array.size()];
      for (int i = 0; i < array.size(); i++) {
        result[i] = convertJsonElement(array.get(i));
      }
      return result;
    }

    if (element.isJsonObject()) {
      return convertJsonToMap(element.getAsJsonObject());
    }

    return element.toString();
  }

  /**
   * Convert Gson JsonObject to Map (recursive).
   *
   * @param json JsonObject to convert
   * @return Map with converted values
   */
  public static Map<String, Object> convertJsonToMap(JsonObject json) {
    if (json == null) {
      return null;
    }

    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
      result.put(entry.getKey(), convertJsonElement(entry.getValue()));
    }
    return result;
  }
}
