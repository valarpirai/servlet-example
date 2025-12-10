package com.example.servlet.processor;

import com.example.servlet.util.JsonUtil;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

public class JsonDataProcessor implements RequestProcessor {

    private static final String CONTENT_TYPE = "application/json";

    @Override
    public boolean supports(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith(CONTENT_TYPE);
    }

    @Override
    public ProcessorResponse process(HttpServletRequest request) throws IOException, ServletException {
        try {
            // Read request body
            String jsonBody = readRequestBody(request);

            if (jsonBody == null || jsonBody.trim().isEmpty()) {
                return ProcessorResponse.builder()
                        .statusCode(400)
                        .body(JsonUtil.errorResponse("Bad Request", "Empty JSON body", 400))
                        .build();
            }

            // Validate JSON
            if (!JsonUtil.isValidJson(jsonBody)) {
                return ProcessorResponse.builder()
                        .statusCode(400)
                        .body(JsonUtil.errorResponse("Bad Request", "Invalid JSON format", 400))
                        .build();
            }

            // Parse JSON to generic object
            Object parsedJson = JsonUtil.fromJson(jsonBody, Object.class);

            // Create response with received data
            Map<String, Object> responseData = Map.of(
                    "received", parsedJson,
                    "size", jsonBody.length()
            );
            String responseBody = JsonUtil.successResponse(responseData);

            return ProcessorResponse.builder()
                    .statusCode(200)
                    .body(responseBody)
                    .build();

        } catch (JsonSyntaxException e) {
            return ProcessorResponse.builder()
                    .statusCode(400)
                    .body(JsonUtil.errorResponse(
                            "Bad Request",
                            "Malformed JSON: " + e.getMessage(),
                            400))
                    .build();
        } catch (Exception e) {
            return ProcessorResponse.builder()
                    .statusCode(500)
                    .body(JsonUtil.errorResponse(
                            "Internal Server Error",
                            "Error processing JSON: " + e.getMessage(),
                            500))
                    .build();
        }
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

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
}
