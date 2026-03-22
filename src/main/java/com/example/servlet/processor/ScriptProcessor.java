package com.example.servlet.processor;

import com.example.servlet.model.Module;
import com.example.servlet.model.ProcessorResponse;
import com.example.servlet.module.ModuleDependencyResolver;
import com.example.servlet.module.ModuleManager;
import com.example.servlet.script.ScriptExecutor;
import com.example.servlet.security.ScriptSecurityManager;
import com.example.servlet.util.JsonConverter;
import com.example.servlet.util.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class ScriptProcessor implements IRequestProcessor {

  private static final String CONTENT_TYPE = "application/javascript";
  private final ScriptExecutor scriptExecutor = new ScriptExecutor();

  @Override
  public boolean supports(String contentType) {
    return contentType != null
        && (contentType.toLowerCase().startsWith(CONTENT_TYPE)
            || contentType.toLowerCase().startsWith("text/javascript"));
  }

  @Override
  public ProcessorResponse process(HttpServletRequest request)
      throws IOException, ServletException {
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
        paramsJson
            .entrySet()
            .forEach(
                entry -> {
                  params.put(entry.getKey(), JsonConverter.convertJsonElement(entry.getValue()));
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

      return ProcessorResponse.builder().statusCode(200).body(responseBody).build();

    } catch (Error e) {
      // Handle timeout and memory limit errors
      if (e.getMessage() != null && e.getMessage().contains("timeout exceeded")) {
        return ProcessorResponse.builder()
            .statusCode(408)
            .body(JsonUtil.errorResponse("Request Timeout", e.getMessage(), 408))
            .build();
      } else if (e.getMessage() != null && e.getMessage().contains("memory limit exceeded")) {
        return ProcessorResponse.builder()
            .statusCode(413)
            .body(JsonUtil.errorResponse("Payload Too Large", e.getMessage(), 413))
            .build();
      }
      return ProcessorResponse.builder()
          .statusCode(500)
          .body(
              JsonUtil.errorResponse(
                  "Internal Server Error", "Script execution error: " + e.getMessage(), 500))
          .build();
    } catch (org.mozilla.javascript.RhinoException e) {
      return ProcessorResponse.builder()
          .statusCode(400)
          .body(
              JsonUtil.errorResponse(
                  "Script Error", "JavaScript execution failed: " + e.getMessage(), 400))
          .build();
    } catch (Exception e) {
      return ProcessorResponse.builder()
          .statusCode(500)
          .body(
              JsonUtil.errorResponse(
                  "Internal Server Error", "Error processing script: " + e.getMessage(), 500))
          .build();
    }
  }

  @Override
  public String getContentType() {
    return CONTENT_TYPE;
  }

  /** Transform ES6 imports to require() calls and load modules */
  private String prepareScriptWithModules(String script, Scriptable scope) throws IOException {
    // Resolve module dependencies
    ModuleManager moduleManager = ModuleManager.getInstance();
    ModuleDependencyResolver resolver = new ModuleDependencyResolver(moduleManager);

    List<String> moduleOrder = resolver.resolveImports(script);

    // Build module loading code
    StringBuilder moduleCode = new StringBuilder();

    // Create module cache
    moduleCode.append("var __moduleCache = {};\n");

    // Load modules in dependency order
    for (String modulePath : moduleOrder) {
      Module module = moduleManager.getModule(modulePath);
      if (module == null) {
        throw new IOException("Module not found: " + modulePath);
      }

      // Wrap module in CommonJS pattern
      moduleCode.append("(function() {\n");
      moduleCode.append("  var module = { exports: {} };\n");
      moduleCode.append("  var exports = module.exports;\n");
      moduleCode.append("  var require = function(path) {\n");
      moduleCode.append("    if (__moduleCache[path]) return __moduleCache[path];\n");
      moduleCode.append("    throw new Error('Module not found: ' + path);\n");
      moduleCode.append("  };\n");
      moduleCode.append("  \n");
      moduleCode.append(module.getContent());
      moduleCode.append("  \n");
      moduleCode.append("  __moduleCache['").append(modulePath).append("'] = module.exports;\n");
      moduleCode.append("})();\n\n");
    }

    // Create global require function
    moduleCode.append("function require(modulePath) {\n");
    moduleCode.append("  if (__moduleCache[modulePath]) {\n");
    moduleCode.append("    return __moduleCache[modulePath];\n");
    moduleCode.append("  }\n");
    moduleCode.append("  throw new Error('Module not found: ' + modulePath);\n");
    moduleCode.append("}\n\n");

    // Transform ES6 imports to require() calls
    String transformedScript =
        script.replaceAll(
            "import\\s+([\\w{},\\s*]+)\\s+from\\s+['\"]([^'\"]+)['\"]", "var $1 = require('$2')");

    return moduleCode.toString() + transformedScript;
  }

  /** Execute JavaScript code using Mozilla Rhino with timeout and memory limits */
  private Object executeScript(
      String script,
      Map<String, Object> params,
      HttpServletRequest request,
      java.util.List<String> consoleLogs) {
    // Track memory usage before execution
    Runtime runtime = Runtime.getRuntime();
    long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

    // Create a timeout tracker
    final long startTime = System.currentTimeMillis();
    final boolean[] timedOut = {false};

    Context context = Context.enter();
    try {
      // Set optimization level (-1 for interpreted mode, good for security)
      context.setOptimizationLevel(ScriptSecurityManager.getOptimizationLevel());

      // Set instruction observer for timeout enforcement
      context.setInstructionObserverThreshold(ScriptSecurityManager.getInstructionThreshold());
      context.setGenerateObserverCount(true);

      // Create observer callback for timeout checking
      org.mozilla.javascript.ContextFactory.getGlobal()
          .addListener(
              new org.mozilla.javascript.ContextFactory.Listener() {
                @Override
                public void contextCreated(Context cx) {}

                @Override
                public void contextReleased(Context cx) {}
              });

      // Create a new scope
      Scriptable scope = context.initStandardObjects();

      // Add server-side context objects
      addServerContext(scope, params, request, consoleLogs);

      // Prepare script with module loading
      String preparedScript;
      try {
        preparedScript = prepareScriptWithModules(script, scope);
      } catch (IOException e) {
        throw new RuntimeException("Module loading failed: " + e.getMessage(), e);
      }

      // Execute the script with timeout and memory monitoring
      Object result = executeWithLimits(context, scope, preparedScript, startTime, memoryBefore);

      // Convert result to Java object
      return scriptExecutor.convertToJavaObject(result);

    } catch (Error e) {
      // Re-throw timeout and memory limit errors to be handled by process()
      if (e.getMessage() != null
          && (e.getMessage().contains("timeout exceeded")
              || e.getMessage().contains("memory limit exceeded"))) {
        throw e;
      }
      throw new RuntimeException("Script execution error: " + e.getMessage(), e);
    } finally {
      Context.exit();
    }
  }

  /** Execute script with timeout and memory limit enforcement */
  private Object executeWithLimits(
      Context context, Scriptable scope, String script, long startTime, long memoryBefore) {
    // Use shared security manager to create secure context factory
    ScriptSecurityManager.SecureContextFactory factory =
        ScriptSecurityManager.createSecureContextFactory(startTime, memoryBefore);

    // Exit current context and use our custom factory
    Context.exit();
    Context cx = factory.enterContext();

    try {
      cx.setOptimizationLevel(ScriptSecurityManager.getOptimizationLevel());
      cx.setInstructionObserverThreshold(ScriptSecurityManager.getInstructionThreshold());

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

  /** Add server context objects to the script scope */
  private void addServerContext(
      Scriptable scope,
      Map<String, Object> params,
      HttpServletRequest request,
      java.util.List<String> consoleLogs) {
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
      request
          .getParameterMap()
          .forEach(
              (key, values) -> {
                queryParams.put(key, values.length > 0 ? values[0] : "");
              });
    }
    serverContext.put("queryParams", queryParams);

    // Add server context as a global variable
    ScriptableObject.putProperty(scope, "request", Context.javaToJS(serverContext, scope));

    // Add utility functions
    addUtilityFunctions(scope, consoleLogs);
  }

  /** Add utility functions to the script scope */
  private void addUtilityFunctions(Scriptable scope, java.util.List<String> consoleLogs) {
    // Add JSON utilities
    String jsonUtils =
        "var JSON = {"
            + "  stringify: function(obj) { return String(obj); },"
            + "  parse: function(str) { return eval('(' + str + ')'); }"
            + "};";

    Context context = Context.getCurrentContext();
    context.evaluateString(scope, jsonUtils, "jsonUtils", 1, null);

    // Expose the console logs list to JavaScript
    ScriptableObject.putProperty(scope, "__consoleLogs", Context.javaToJS(consoleLogs, scope));

    // Add console.log function that captures output
    String consoleLog =
        "var console = {"
            + "  log: function() { "
            + "    var args = Array.prototype.slice.call(arguments);"
            + "    var message = args.join(' ');"
            + "    __consoleLogs.add(message);"
            + "    return message;"
            + "  }"
            + "};";

    context.evaluateString(scope, consoleLog, "consoleLog", 1, null);
  }

  /** Read request body */
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
