package com.example.servlet.processor;

import com.example.servlet.util.JsonUtil;
import com.example.servlet.util.PropertiesUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ClassShutter;
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
    private static final long MAX_MEMORY_BYTES = PropertiesUtil.getLong("script.maxMemory", 10485760L); // 10 MB default
    private static final int INSTRUCTION_OBSERVATION_THRESHOLD = PropertiesUtil.getInt("script.instructionThreshold", 10000);

    /**
     * ClassShutter implementation using whitelist and blacklist approach
     * - Whitelist: Explicitly safe classes that are always allowed
     * - Blacklist: Dangerous classes/packages that are always blocked
     * - Default: Classes not in whitelist or blacklist are evaluated by package pattern
     */
    private static class SandboxClassShutter implements ClassShutter {
        // Whitelist: Explicitly safe classes that are always allowed
        private static final java.util.Set<String> ALLOWED_CLASSES = java.util.Set.of(
            // Common collections
            "java.util.ArrayList",
            "java.util.HashMap",
            "java.util.HashSet",
            "java.util.LinkedList",
            "java.util.TreeMap",
            "java.util.TreeSet",
            "java.util.LinkedHashMap",
            "java.util.LinkedHashSet",
            "java.util.Vector",
            "java.util.Stack",

            // Common utilities
            "java.util.Date",
            "java.util.UUID",
            "java.util.Optional",
            "java.util.Arrays",
            "java.util.Collections",

            // String and primitives
            "java.lang.String",
            "java.lang.StringBuilder",
            "java.lang.StringBuffer",
            "java.lang.Math",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Boolean",
            "java.lang.Character",
            "java.lang.Byte",
            "java.lang.Short",

            // Date/Time (Java 8+)
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.LocalTime",
            "java.time.Instant",
            "java.time.Duration",
            "java.time.Period",
            "java.time.ZonedDateTime",
            "java.time.ZoneId",

            // Math
            "java.math.BigDecimal",
            "java.math.BigInteger"
        );

        // Blacklist: Dangerous classes and package prefixes
        private static final java.util.Set<String> BLOCKED_CLASSES = java.util.Set.of(
            "java.lang.System",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Process",
            "java.lang.ClassLoader",
            "java.lang.Thread",
            "java.lang.ThreadGroup",
            "java.lang.SecurityManager"
        );

        private static final java.util.Set<String> BLOCKED_PACKAGE_PREFIXES = java.util.Set.of(
            "java.io.",                 // File system access
            "java.nio.file.",           // File system access
            "java.net.",                // Network access
            "java.lang.reflect.",       // Reflection
            "java.lang.invoke.",        // Method handles
            "javax.script.",            // Script engine access
            "sun.",                     // Internal Sun classes
            "com.sun.",                 // Internal Sun classes
            "jdk.",                     // Internal JDK classes
            "java.security.",           // Security manager manipulation
            "javax.naming.",            // JNDI (potential RCE)
            "javax.management.",        // JMX (management access)
            "java.sql.",                // Database access
            "javax.sql."                // Database access
        );

        @Override
        public boolean visibleToScripts(String className) {
            // 1. Check whitelist first (explicitly allowed)
            if (ALLOWED_CLASSES.contains(className)) {
                return true;
            }

            // 2. Check blacklist (explicitly blocked classes)
            if (BLOCKED_CLASSES.contains(className)) {
                return false;
            }

            // 3. Check blacklisted package prefixes
            for (String prefix : BLOCKED_PACKAGE_PREFIXES) {
                if (className.startsWith(prefix)) {
                    return false;
                }
            }

            // 4. Allow other java.util and java.lang classes by default
            if (className.startsWith("java.util.") || className.startsWith("java.lang.")) {
                return true;
            }

            // 5. Block everything else by default
            return false;
        }
    }

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

            // Create a list to capture console logs
            java.util.List<String> consoleLogs = new java.util.ArrayList<>();

            // Track execution metrics
            Runtime runtime = Runtime.getRuntime();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
            long startTime = System.currentTimeMillis();

            // Execute the script
            Object result = executeScript(script, params, request, consoleLogs);

            // Calculate execution metrics
            long endTime = System.currentTimeMillis();
            long executionTimeMs = endTime - startTime;
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsedBytes = memoryAfter - memoryBefore;

            // Build response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("result", result);
            responseData.put("executionTimeMs", executionTimeMs);
            responseData.put("memoryUsedBytes", memoryUsedBytes);
            if (!consoleLogs.isEmpty()) {
                responseData.put("console", consoleLogs);
            }

            String responseBody = JsonUtil.successResponse(responseData);

            return ProcessorResponse.builder()
                    .statusCode(200)
                    .body(responseBody)
                    .build();

        } catch (Error e) {
            // Handle timeout and memory limit errors
            if (e.getMessage() != null && e.getMessage().contains("timeout exceeded")) {
                return ProcessorResponse.builder()
                        .statusCode(408)
                        .body(JsonUtil.errorResponse(
                                "Request Timeout",
                                e.getMessage(),
                                408))
                        .build();
            } else if (e.getMessage() != null && e.getMessage().contains("memory limit exceeded")) {
                return ProcessorResponse.builder()
                        .statusCode(413)
                        .body(JsonUtil.errorResponse(
                                "Payload Too Large",
                                e.getMessage(),
                                413))
                        .build();
            }
            return ProcessorResponse.builder()
                    .statusCode(500)
                    .body(JsonUtil.errorResponse(
                            "Internal Server Error",
                            "Script execution error: " + e.getMessage(),
                            500))
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
     * Execute JavaScript code using Mozilla Rhino with timeout and memory limits
     */
    private Object executeScript(String script, Map<String, Object> params, HttpServletRequest request, java.util.List<String> consoleLogs) {
        // Track memory usage before execution
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Create a timeout tracker
        final long startTime = System.currentTimeMillis();
        final boolean[] timedOut = {false};

        Context context = Context.enter();
        try {
            // Set optimization level (-1 for interpreted mode, good for security)
            context.setOptimizationLevel(OPTIMIZATION_LEVEL);

            // Set instruction observer for timeout enforcement
            context.setInstructionObserverThreshold(INSTRUCTION_OBSERVATION_THRESHOLD);
            context.setGenerateObserverCount(true);

            // Create observer callback for timeout checking
            org.mozilla.javascript.ContextFactory.getGlobal().addListener(new org.mozilla.javascript.ContextFactory.Listener() {
                @Override
                public void contextCreated(Context cx) {}

                @Override
                public void contextReleased(Context cx) {}
            });

            // Create a new scope
            Scriptable scope = context.initStandardObjects();

            // Add server-side context objects
            addServerContext(scope, params, request, consoleLogs);

            // Execute the script with timeout and memory monitoring
            Object result = executeWithLimits(context, scope, script, startTime, memoryBefore);

            // Convert result to Java object
            return convertRhinoObject(result);

        } catch (Error e) {
            // Re-throw timeout and memory limit errors to be handled by process()
            if (e.getMessage() != null &&
                (e.getMessage().contains("timeout exceeded") || e.getMessage().contains("memory limit exceeded"))) {
                throw e;
            }
            throw new RuntimeException("Script execution error: " + e.getMessage(), e);
        } finally {
            Context.exit();
        }
    }

    /**
     * Execute script with timeout and memory limit enforcement
     */
    private Object executeWithLimits(Context context, Scriptable scope, String script, long startTime, long memoryBefore) {
        // Set up instruction observer for timeout
        final long timeoutMillis = SCRIPT_TIMEOUT;
        final long maxMemory = MAX_MEMORY_BYTES;

        context.setInstructionObserverThreshold(INSTRUCTION_OBSERVATION_THRESHOLD);

        // Custom observer to check timeout and memory
        org.mozilla.javascript.ContextFactory factory = new org.mozilla.javascript.ContextFactory() {
            @Override
            protected void observeInstructionCount(Context cx, int instructionCount) {
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - startTime;

                // Check timeout
                if (elapsed > timeoutMillis) {
                    throw new Error("Script execution timeout exceeded: " + elapsed + "ms > " + timeoutMillis + "ms");
                }

                // Check memory usage
                Runtime runtime = Runtime.getRuntime();
                long memoryNow = runtime.totalMemory() - runtime.freeMemory();
                long memoryUsed = memoryNow - memoryBefore;

                if (memoryUsed > maxMemory) {
                    throw new Error("Script memory limit exceeded: " + memoryUsed + " bytes > " + maxMemory + " bytes");
                }
            }

            @Override
            protected Context makeContext() {
                Context cx = super.makeContext();
                cx.setOptimizationLevel(OPTIMIZATION_LEVEL);
                cx.setInstructionObserverThreshold(INSTRUCTION_OBSERVATION_THRESHOLD);
                // Apply ClassShutter to block Java class access for security
                cx.setClassShutter(new SandboxClassShutter());
                return cx;
            }
        };

        // Exit current context and use our custom factory
        Context.exit();
        Context cx = factory.enterContext();

        try {
            cx.setOptimizationLevel(OPTIMIZATION_LEVEL);
            cx.setInstructionObserverThreshold(INSTRUCTION_OBSERVATION_THRESHOLD);

            // Re-initialize scope in new context
            Scriptable newScope = cx.initStandardObjects();

            // Copy all properties from old scope to new scope
            for (Object id : scope.getIds()) {
                if (id instanceof String) {
                    String key = (String) id;
                    Object value = scope.get(key, scope);
                    newScope.put(key, newScope, value);
                } else if (id instanceof Integer) {
                    int index = (Integer) id;
                    Object value = scope.get(index, scope);
                    newScope.put(index, newScope, value);
                }
            }

            return cx.evaluateString(newScope, script, "userScript", 1, null);
        } finally {
            Context.exit();
            // Re-enter original context for cleanup
            context = Context.enter();
        }
    }

    /**
     * Add server context objects to the script scope
     */
    private void addServerContext(Scriptable scope, Map<String, Object> params, HttpServletRequest request, java.util.List<String> consoleLogs) {
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
        addUtilityFunctions(scope, consoleLogs);
    }

    /**
     * Add utility functions to the script scope
     */
    private void addUtilityFunctions(Scriptable scope, java.util.List<String> consoleLogs) {
        // Add JSON utilities
        String jsonUtils =
            "var JSON = {" +
            "  stringify: function(obj) { return String(obj); }," +
            "  parse: function(str) { return eval('(' + str + ')'); }" +
            "};";

        Context context = Context.getCurrentContext();
        context.evaluateString(scope, jsonUtils, "jsonUtils", 1, null);

        // Expose the console logs list to JavaScript
        ScriptableObject.putProperty(scope, "__consoleLogs", Context.javaToJS(consoleLogs, scope));

        // Add console.log function that captures output
        String consoleLog =
            "var console = {" +
            "  log: function() { " +
            "    var args = Array.prototype.slice.call(arguments);" +
            "    var message = args.join(' ');" +
            "    __consoleLogs.add(message);" +
            "    return message;" +
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
