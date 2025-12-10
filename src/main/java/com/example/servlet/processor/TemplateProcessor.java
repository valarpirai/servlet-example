package com.example.servlet.processor;

import com.example.servlet.util.JsonUtil;
import com.example.servlet.util.TemplateEngine;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TemplateProcessor implements RequestProcessor {

    private static final String CONTENT_TYPE = "text/html";

    @Override
    public boolean supports(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("text/html");
    }

    @Override
    public ProcessorResponse process(HttpServletRequest request) throws IOException, ServletException {
        try {
            // Read request body (expected to be JSON with template and data)
            String requestBody = readRequestBody(request);

            if (requestBody == null || requestBody.trim().isEmpty()) {
                return ProcessorResponse.builder()
                        .statusCode(400)
                        .contentType("application/json")
                        .body(JsonUtil.errorResponse("Bad Request", "Empty request body", 400))
                        .build();
            }

            // Parse JSON
            JsonObject json = JsonParser.parseString(requestBody).getAsJsonObject();

            // Get template path or inline template
            String templatePath = null;
            String inlineTemplate = null;

            if (json.has("templatePath")) {
                templatePath = json.get("templatePath").getAsString();
            } else if (json.has("template")) {
                inlineTemplate = json.get("template").getAsString();
            } else {
                return ProcessorResponse.builder()
                        .statusCode(400)
                        .contentType("application/json")
                        .body(JsonUtil.errorResponse("Bad Request",
                                "Missing 'templatePath' or 'template' field", 400))
                        .build();
            }

            // Get data for template
            Map<String, Object> data = new HashMap<>();
            if (json.has("data") && json.get("data").isJsonObject()) {
                JsonObject dataJson = json.getAsJsonObject("data");
                data = convertJsonObjectToMap(dataJson);
            }

            // Load or use template
            String templateContent;
            if (templatePath != null) {
                try {
                    templateContent = TemplateEngine.loadTemplate("templates/" + templatePath);
                } catch (IOException e) {
                    return ProcessorResponse.builder()
                            .statusCode(404)
                            .contentType("application/json")
                            .body(JsonUtil.errorResponse("Not Found",
                                    "Template not found: " + templatePath, 404))
                            .build();
                }
            } else {
                templateContent = inlineTemplate;
            }

            // Render template
            String renderedHtml = TemplateEngine.render(templateContent, data);

            // Return HTML response
            return ProcessorResponse.builder()
                    .statusCode(200)
                    .contentType("text/html; charset=UTF-8")
                    .body(renderedHtml)
                    .build();

        } catch (com.google.gson.JsonSyntaxException e) {
            return ProcessorResponse.builder()
                    .statusCode(400)
                    .contentType("application/json")
                    .body(JsonUtil.errorResponse("Bad Request",
                            "Invalid JSON: " + e.getMessage(), 400))
                    .build();
        } catch (Exception e) {
            return ProcessorResponse.builder()
                    .statusCode(500)
                    .contentType("application/json")
                    .body(JsonUtil.errorResponse("Internal Server Error",
                            "Error rendering template: " + e.getMessage(), 500))
                    .build();
        }
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    /**
     * Read request body
     */
    private String readRequestBody(HttpServletRequest request) throws IOException {
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
     * Convert JsonObject to Map recursively
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertJsonObjectToMap(JsonObject jsonObject) {
        Map<String, Object> map = new HashMap<>();

        jsonObject.entrySet().forEach(entry -> {
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
                map.put(key, convertJsonObjectToMap(value.getAsJsonObject()));
            } else if (value.isJsonArray()) {
                java.util.List<Object> list = new java.util.ArrayList<>();
                value.getAsJsonArray().forEach(element -> {
                    if (element.isJsonObject()) {
                        list.add(convertJsonObjectToMap(element.getAsJsonObject()));
                    } else if (element.isJsonPrimitive()) {
                        list.add(element.getAsString());
                    }
                });
                map.put(key, list);
            }
        });

        return map;
    }
}
