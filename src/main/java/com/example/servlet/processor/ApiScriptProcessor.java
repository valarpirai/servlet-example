package com.example.servlet.processor;

import com.example.servlet.security.ScriptSecurityManager;
import com.example.servlet.util.JsonUtil;
import com.example.servlet.util.PropertiesUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * Processor for scripted REST API endpoints. Loads JavaScript files from scripts/api/ directory and
 * executes them with request/response handling. Supports require() for loading shared libraries
 * from scripts/lib/ and scripts/vendor/.
 */
public class ApiScriptProcessor {

  private static final String SCRIPTS_BASE_DIR = "scripts";
  private static final String API_DIR = SCRIPTS_BASE_DIR + "/api";
  private static final String LIB_DIR = SCRIPTS_BASE_DIR + "/lib";
  private static final String VENDOR_DIR = SCRIPTS_BASE_DIR + "/vendor";

  // Cache for loaded scripts (module path -> script content)
  private final Map<String, String> scriptCache = new HashMap<>();

  /**
   * Process an API request with the given endpoint script.
   *
   * @param endpointName The endpoint name (e.g., "users" for /api/v1/users)
   * @param method HTTP method (GET, POST, etc.)
   * @param path Request path
   * @param queryParams Query parameters
   * @param headers Request headers
   * @param body Request body (parsed JSON)
   * @return Map containing response data (status, headers, body)
   */
  public Map<String, Object> processRequest(
      String endpointName,
      String method,
      String path,
      Map<String, String> queryParams,
      Map<String, String> headers,
      JsonObject body) {

    try {
      // Load the endpoint script
      String scriptPath = API_DIR + "/" + endpointName + ".js";
      String scriptContent = loadScript(scriptPath);

      if (scriptContent == null) {
        return createErrorResponse(
            404, "Not Found", "Endpoint script not found: " + endpointName, null);
      }

      // Execute the script
      return executeScript(scriptContent, method, path, queryParams, headers, body);

    } catch (Exception e) {
      return createErrorResponse(
          500, "Internal Server Error", "Error processing API request: " + e.getMessage(), e);
    }
  }

  /**
   * Load a script file from the file system.
   *
   * @param scriptPath Relative path from project root (e.g., "scripts/api/users.js")
   * @return Script content or null if not found
   */
  private String loadScript(String scriptPath) throws IOException {
    // Check cache first
    if (scriptCache.containsKey(scriptPath)) {
      return scriptCache.get(scriptPath);
    }

    Path path = Paths.get(scriptPath);
    if (!Files.exists(path)) {
      return null;
    }

    String content = Files.readString(path);

    // Cache the script (in production, consider cache invalidation strategy)
    if (!PropertiesUtil.isDevEnvironment()) {
      scriptCache.put(scriptPath, content);
    }

    return content;
  }

  /** Execute the JavaScript handler with request/response objects. */
  private Map<String, Object> executeScript(
      String script,
      String method,
      String path,
      Map<String, String> queryParams,
      Map<String, String> headers,
      JsonObject body) {

    // Track memory usage before execution
    Runtime runtime = Runtime.getRuntime();
    long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
    long startTime = System.currentTimeMillis();

    // Use shared security manager
    ScriptSecurityManager.SecureContextFactory factory =
        ScriptSecurityManager.createSecureContextFactory(startTime, memoryBefore);

    Context cx = factory.enterContext();

    try {
      cx.setOptimizationLevel(ScriptSecurityManager.getOptimizationLevel());
      cx.setInstructionObserverThreshold(ScriptSecurityManager.getInstructionThreshold());

      Scriptable scope = cx.initStandardObjects();

      // Add require() function
      addRequireFunction(scope, cx);

      // Add request object as pure JavaScript
      Map<String, Object> requestData = new HashMap<>();
      requestData.put("method", method);
      requestData.put("path", path);
      requestData.put("queryParams", queryParams);
      requestData.put("headers", headers);
      requestData.put("body", convertJsonToJavaScript(body));

      ScriptableObject.putProperty(scope, "request", Context.javaToJS(requestData, scope));

      // Create response object in JavaScript (not Java)
      String responseInit =
          """
          var response = {
            status: 200,
            headers: {},
            body: '',
            setStatus: function(s) { this.status = s; },
            setHeader: function(k, v) { this.headers[k] = v; },
            setBody: function(b) {
              if (typeof b === 'string') {
                this.body = b;
              } else {
                this.body = JSON.stringify(b);
              }
            }
          };
          """;
      cx.evaluateString(scope, responseInit, "responseInit", 1, null);

      // Execute the script
      cx.evaluateString(scope, script, "apiScript", 1, null);

      // Check if httpHandler function exists and call it
      Object httpHandler = scope.get("httpHandler", scope);
      if (httpHandler instanceof org.mozilla.javascript.Function) {
        org.mozilla.javascript.Function func = (org.mozilla.javascript.Function) httpHandler;
        Object requestObj = scope.get("request", scope);
        Object responseObj = scope.get("response", scope);
        func.call(cx, scope, scope, new Object[] {requestObj, responseObj});
      }

      // Extract response data from JavaScript object
      Object responseObj = scope.get("response", scope);
      Map<String, Object> response = new HashMap<>();

      if (responseObj instanceof Scriptable) {
        Scriptable responseScriptable = (Scriptable) responseObj;

        // Convert status to integer
        Object statusObj = responseScriptable.get("status", responseScriptable);
        int status = 200;
        if (statusObj instanceof Number) {
          status = ((Number) statusObj).intValue();
        }

        response.put("status", status);
        response.put(
            "headers", convertToJavaObject(responseScriptable.get("headers", responseScriptable)));
        response.put(
            "body", convertToJavaObject(responseScriptable.get("body", responseScriptable)));
      } else {
        response.put("status", 200);
        response.put("headers", new HashMap<String, String>());
        response.put("body", "");
      }

      return response;

    } catch (org.mozilla.javascript.RhinoException e) {
      return createErrorResponse(
          400, "Script Error", "JavaScript execution failed: " + e.getMessage(), e);
    } catch (Error e) {
      if (e.getMessage() != null && e.getMessage().contains("timeout exceeded")) {
        return createErrorResponse(408, "Request Timeout", e.getMessage(), e);
      } else if (e.getMessage() != null && e.getMessage().contains("memory limit exceeded")) {
        return createErrorResponse(413, "Payload Too Large", e.getMessage(), e);
      }
      return createErrorResponse(
          500, "Internal Server Error", "Script execution error: " + e.getMessage(), e);
    } catch (Exception e) {
      return createErrorResponse(
          500, "Internal Server Error", "Error executing script: " + e.getMessage(), e);
    } finally {
      Context.exit();
    }
  }

  /** Add require() function to the scope for loading modules. */
  private void addRequireFunction(Scriptable scope, Context cx) {
    // Create a require function that loads scripts from lib and vendor directories
    String requireFunction =
        """
        var __loadedModules = {};

        function require(modulePath) {
          // Normalize path
          var normalizedPath = modulePath;

          // If path starts with ../ or ./, resolve relative to current directory
          if (modulePath.startsWith('../')) {
            normalizedPath = modulePath.substring(3);
          } else if (modulePath.startsWith('./')) {
            normalizedPath = modulePath.substring(2);
          }

          // Check if already loaded
          if (__loadedModules[normalizedPath]) {
            return __loadedModules[normalizedPath];
          }

          // Try to load from lib or vendor
          var content = __loadScriptFile(normalizedPath);
          if (!content) {
            throw new Error('Module not found: ' + modulePath);
          }

          // Create module wrapper
          var module = { exports: {} };
          var exports = module.exports;

          // Execute module code
          eval(content);

          // Cache the module
          __loadedModules[normalizedPath] = module.exports;

          return module.exports;
        }
        """;

    cx.evaluateString(scope, requireFunction, "require", 1, null);

    // Add Java function to actually load script files
    ScriptableObject.putProperty(
        scope,
        "__loadScriptFile",
        new org.mozilla.javascript.BaseFunction() {
          @Override
          public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            if (args.length < 1 || !(args[0] instanceof String)) {
              return null;
            }

            String modulePath = (String) args[0];

            // Try lib directory first, then vendor
            String[] searchPaths = {LIB_DIR + "/" + modulePath, VENDOR_DIR + "/" + modulePath};

            for (String searchPath : searchPaths) {
              // Add .js extension if not present
              String fullPath = searchPath.endsWith(".js") ? searchPath : searchPath + ".js";

              try {
                String content = loadScript(fullPath);
                if (content != null) {
                  return content;
                }
              } catch (IOException e) {
                // Continue to next search path
              }
            }

            return null;
          }
        });
  }

  /** Convert Gson JsonObject to JavaScript-friendly Map. */
  private Map<String, Object> convertJsonToJavaScript(JsonObject json) {
    if (json == null) {
      return null;
    }

    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
      result.put(entry.getKey(), convertJsonElement(entry.getValue()));
    }
    return result;
  }

  /** Convert JavaScript object to Java object. */
  private Object convertToJavaObject(Object obj) {
    if (obj == null || obj == org.mozilla.javascript.Undefined.instance) {
      return null;
    }

    if (obj instanceof org.mozilla.javascript.NativeArray) {
      org.mozilla.javascript.NativeArray array = (org.mozilla.javascript.NativeArray) obj;
      Object[] result = new Object[(int) array.getLength()];
      for (int i = 0; i < array.getLength(); i++) {
        result[i] = convertToJavaObject(array.get(i));
      }
      return result;
    }

    if (obj instanceof org.mozilla.javascript.NativeObject) {
      org.mozilla.javascript.NativeObject nativeObj = (org.mozilla.javascript.NativeObject) obj;
      Map<String, Object> result = new HashMap<>();
      for (Object key : nativeObj.keySet()) {
        result.put(key.toString(), convertToJavaObject(nativeObj.get(key)));
      }
      return result;
    }

    if (obj instanceof org.mozilla.javascript.Scriptable) {
      // Generic Scriptable handling
      org.mozilla.javascript.Scriptable scriptable = (org.mozilla.javascript.Scriptable) obj;
      Map<String, Object> result = new HashMap<>();
      Object[] ids = scriptable.getIds();
      for (Object id : ids) {
        if (id instanceof String) {
          String key = (String) id;
          result.put(key, convertToJavaObject(scriptable.get(key, scriptable)));
        }
      }
      return result;
    }

    if (obj instanceof Number || obj instanceof String || obj instanceof Boolean) {
      return obj;
    }

    return obj.toString();
  }

  /** Convert JsonElement to Java object. */
  private Object convertJsonElement(JsonElement element) {
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
    } else if (element.isJsonArray()) {
      var array = element.getAsJsonArray();
      Object[] result = new Object[array.size()];
      for (int i = 0; i < array.size(); i++) {
        result[i] = convertJsonElement(array.get(i));
      }
      return result;
    } else if (element.isJsonObject()) {
      return convertJsonToJavaScript(element.getAsJsonObject());
    }
    return null;
  }

  /** Create an error response map. */
  private Map<String, Object> createErrorResponse(
      int status, String error, String message, Throwable throwable) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", status);
    response.put("headers", new HashMap<String, String>());

    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("error", error);
    errorBody.put("message", message);

    // Include stack trace in dev mode
    if (PropertiesUtil.isDevEnvironment() && throwable != null) {
      StringBuilder stackTrace = new StringBuilder();
      stackTrace.append(throwable.toString()).append("\n");
      for (StackTraceElement element : throwable.getStackTrace()) {
        stackTrace.append("  at ").append(element.toString()).append("\n");
      }
      errorBody.put("stackTrace", stackTrace.toString());
    }

    response.put("body", JsonUtil.toJson(errorBody));

    return response;
  }
}
