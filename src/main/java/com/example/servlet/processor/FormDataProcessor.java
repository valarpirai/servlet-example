package com.example.servlet.processor;

import com.example.servlet.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FormDataProcessor implements RequestProcessor {

    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    @Override
    public boolean supports(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith(CONTENT_TYPE);
    }

    @Override
    public ProcessorResponse process(HttpServletRequest request) throws IOException, ServletException {
        try {
            // Extract form parameters
            Map<String, String[]> parameterMap = request.getParameterMap();

            if (parameterMap.isEmpty()) {
                return ProcessorResponse.builder()
                        .statusCode(400)
                        .body(JsonUtil.errorResponse("Bad Request", "No form data provided", 400))
                        .build();
            }

            // Convert to single-value map for simplicity
            Map<String, Object> formData = new HashMap<>();
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                String[] values = entry.getValue();
                if (values.length == 1) {
                    formData.put(entry.getKey(), values[0]);
                } else {
                    formData.put(entry.getKey(), values);
                }
            }

            // Create response
            String responseBody = JsonUtil.successResponse(formData);

            return ProcessorResponse.builder()
                    .statusCode(200)
                    .body(responseBody)
                    .build();

        } catch (Exception e) {
            return ProcessorResponse.builder()
                    .statusCode(500)
                    .body(JsonUtil.errorResponse(
                            "Internal Server Error",
                            "Error processing form data: " + e.getMessage(),
                            500))
                    .build();
        }
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }
}
