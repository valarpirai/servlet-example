package com.example.servlet.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.util.Map;

public class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    /**
     * Convert an object to JSON string
     *
     * @param object The object to convert
     * @return JSON string representation
     */
    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    /**
     * Parse JSON string to an object
     *
     * @param json The JSON string
     * @param classOfT The class of the target object
     * @param <T> The type of the target object
     * @return The parsed object
     * @throws JsonSyntaxException if JSON is malformed
     */
    public static <T> T fromJson(String json, Class<T> classOfT) throws JsonSyntaxException {
        return GSON.fromJson(json, classOfT);
    }

    /**
     * Create a success response JSON
     *
     * @param data The data to include in the response
     * @return JSON string with status "success"
     */
    public static String successResponse(Object data) {
        Map<String, Object> response = Map.of(
                "status", "success",
                "data", data,
                "timestamp", System.currentTimeMillis()
        );
        return toJson(response);
    }

    /**
     * Create an error response JSON
     *
     * @param error The error type
     * @param message The error message
     * @param statusCode The HTTP status code
     * @return JSON string with error details
     */
    public static String errorResponse(String error, String message, int statusCode) {
        Map<String, Object> response = Map.of(
                "error", error,
                "message", message,
                "status", statusCode,
                "timestamp", System.currentTimeMillis()
        );
        return toJson(response);
    }

    /**
     * Check if a string is valid JSON
     *
     * @param json The string to check
     * @return true if valid JSON, false otherwise
     */
    public static boolean isValidJson(String json) {
        try {
            GSON.fromJson(json, Object.class);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }
}
