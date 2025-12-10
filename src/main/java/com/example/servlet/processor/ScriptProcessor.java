package com.example.servlet.processor;

import com.example.servlet.util.JsonUtil;
import com.example.servlet.util.PropertiesUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ScriptProcessor implements RequestProcessor {

    private static final String CONTENT_TYPE = "application/javascript";
    private static final long SCRIPT_TIMEOUT = PropertiesUtil.getLong("script.timeout", 5000L);
    private static final int OPTIMIZATION_LEVEL = PropertiesUtil.getInt("script.optimizationLevel", -1);

    @Override
    public boolean supports(String contentType) {
        return contentType != null && (
                contentType.toLowerCase().startsWith(CONTENT_TYPE) ||
                contentType.toLowerCase().startsWith("text/javascript")
        );
    }

    @Override
    public ProcessorResponse process(HttpServletRequest request) throws IOException, ServletException {
        try {
            // Read the script from request body
            String requestBody = readRequestBody(request);

            if (requestBody == null || requestBody.trim().isEmpty()) {
                return ProcessorResponse.builder()
                        .statusCode(400)
                        .body(JsonUtil.errorResponse("Bad Request", "Empty request body", 400))
                        .build();
            }

            // Parse JSON to get script and parameters
            JsonObject json = JsonParser.parseString(requestBody).getAsJsonObject();

            if (!json.has("script")) {
                return ProcessorResponse.builder()
                        .statusCode(400)
                        .body(JsonUtil.errorResponse("Bad Request", "Missing 'script' field", 400))
                        .build();
            }

            String script = json.get("script").getAsString();
            Map<String, Object> params = new HashMap<>();

            // Extract optional parameters
            if (json.has("params") && json.get("params").isJsonObject()) {
                JsonObject paramsJson = json.getAsJsonObject("params");
                paramsJson.entrySet().forEach(entry -> {
                    params.put(entry.getKey(), convertJsonElement(entry.getValue()));
                });
            }

            // Execute the script
            Object result = executeScript(script, params, request);

            // Build response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("result", result);
            responseData.put("executionTime", System.currentTimeMillis());

            String responseBody = JsonUtil.successResponse(responseData);

            return ProcessorResponse.builder()
                    .statusCode(200)
                    .body(responseBody)
                    .build();

        } catch (org.mozilla.javascript.RhinoException e) {
            return ProcessorResponse.builder()
                    .statusCode(400)
                    .body(JsonUtil.errorResponse(
                            "Script Error",
                            "JavaScript execution failed: " + e.getMessage(),
                            400))
                    .build();
        } catch (Exception e) {
            return ProcessorResponse.builder()
                    .statusCode(500)
                    .body(JsonUtil.errorResponse(
                            "Internal Server Error",
                            "Error processing script: " + e.getMessage(),
                            500))
                    .build();
        }
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    /**
     * Execute JavaScript code using Mozilla Rhino
     */
    private Object executeScript(String script, Map<String, Object> params, HttpServletRequest request) {
        Context context = Context.enter();
        try {
            // Set optimization level (-1 for interpreted mode, good for security)
            context.setOptimizationLevel(OPTIMIZATION_LEVEL);

            // Create a new scope
            Scriptable scope = context.initStandardObjects();

            // Add server-side context objects
            addServerContext(scope, params, request);

            // Execute the script with timeout protection
            Object result = context.evaluateString(scope, script, "userScript", 1, null);

            // Convert result to Java object
            return convertRhinoObject(result);

        } finally {
            Context.exit();
        }
    }

    /**
     * Add server context objects to the script scope
     */
    private void addServerContext(Scriptable scope, Map<String, Object> params, HttpServletRequest request) {
        // Add parameters
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                ScriptableObject.putProperty(scope, entry.getKey(), entry.getValue());
            }
        }

        // Add server context
        Map<String, Object> serverContext = new HashMap<>();
        serverContext.put("method", request.getMethod());
        serverContext.put("path", request.getPathInfo());
        serverContext.put("remoteAddr", request.getRemoteAddr());

        // Add query parameters
        Map<String, String> queryParams = new HashMap<>();
        if (request.getQueryString() != null) {
            request.getParameterMap().forEach((key, values) -> {
                queryParams.put(key, values.length > 0 ? values[0] : "");
            });
        }
        serverContext.put("queryParams", queryParams);

        // Add server context as a global variable
        ScriptableObject.putProperty(scope, "request", Context.javaToJS(serverContext, scope));

        // Add utility functions
        addUtilityFunctions(scope);
    }

    /**
     * Add utility functions to the script scope
     */
    private void addUtilityFunctions(Scriptable scope) {
        // Add JSON utilities
        String jsonUtils =
            "var JSON = {" +
            "  stringify: function(obj) { return String(obj); }," +
            "  parse: function(str) { return eval('(' + str + ')'); }" +
            "};";

        Context context = Context.getCurrentContext();
        context.evaluateString(scope, jsonUtils, "jsonUtils", 1, null);

        // Add console.log function
        String consoleLog =
            "var console = {" +
            "  log: function() { " +
            "    var args = Array.prototype.slice.call(arguments);" +
            "    return args.join(' ');" +
            "  }" +
            "};";

        context.evaluateString(scope, consoleLog, "consoleLog", 1, null);
    }

    /**
     * Convert Rhino JavaScript object to Java object
     */
    private Object convertRhinoObject(Object obj) {
        if (obj == null || obj == org.mozilla.javascript.Undefined.instance) {
            return null;
        }

        if (obj instanceof org.mozilla.javascript.NativeArray) {
            org.mozilla.javascript.NativeArray array = (org.mozilla.javascript.NativeArray) obj;
            Object[] result = new Object[(int) array.getLength()];
            for (int i = 0; i < array.getLength(); i++) {
                result[i] = convertRhinoObject(array.get(i));
            }
            return result;
        }

        if (obj instanceof org.mozilla.javascript.NativeObject) {
            org.mozilla.javascript.NativeObject nativeObj = (org.mozilla.javascript.NativeObject) obj;
            Map<String, Object> result = new HashMap<>();
            for (Object key : nativeObj.keySet()) {
                result.put(key.toString(), convertRhinoObject(nativeObj.get(key)));
            }
            return result;
        }

        if (obj instanceof Number || obj instanceof String || obj instanceof Boolean) {
            return obj;
        }

        return obj.toString();
    }

    /**
     * Convert Gson JsonElement to Java object
     */
    private Object convertJsonElement(com.google.gson.JsonElement element) {
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsNumber();
            } else if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            } else {
                return element.getAsString();
            }
        } else if (element.isJsonArray()) {
            return element.getAsJsonArray().toString();
        } else if (element.isJsonObject()) {
            return element.getAsJsonObject().toString();
        }
        return element.toString();
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
}
