package com.example.servlet.processor;

import com.example.servlet.script.ScriptExecutor;
import com.example.servlet.util.JsonConverter;
import com.example.servlet.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * Processor for scripted REST API endpoints. Loads JavaScript files from scripts/api/ directory and
 * executes them with request/response handling. Supports require() for loading shared libraries
 * from scripts/lib/ and scripts/vendor/.
 */
public class ApiScriptProcessor {

  private static final String API_DIR = "scripts/api";

  private final ScriptExecutor scriptExecutor = new ScriptExecutor();

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
      String scriptContent = scriptExecutor.loadScript(scriptPath);

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

  /** Execute the JavaScript handler with request/response objects. */
  private Map<String, Object> executeScript(
      String script,
      String method,
      String path,
      Map<String, String> queryParams,
      Map<String, String> headers,
      JsonObject body) {

    // Prepare request data - convert to JSON and back to ensure proper JavaScript object
    Map<String, Object> requestData = new HashMap<>();
    requestData.put("method", method);
    requestData.put("path", path);
    requestData.put("queryParams", queryParams);
    requestData.put("headers", headers);
    requestData.put("body", JsonConverter.convertJsonToMap(body));

    // Convert request data to JSON string for proper JavaScript object creation
    String requestJson = JsonUtil.toJson(requestData);
    // Escape single quotes and newlines for JavaScript string literal
    String escapedRequestJson =
        requestJson
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r");

    // Create request and response initialization script with JSON utilities
    String initScript =
        String.format(
                """
            // JSON utilities
            if (typeof JSON === 'undefined') {
              var JSON = {
                stringify: function(obj) { return String(obj); },
                parse: function(str) { return eval('(' + str + ')'); }
              };
            }

            // Create request object from JSON
            var request = JSON.parse('%s');

            // Create response object
            var response = {""",
                escapedRequestJson)
            + """
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

    // Combined script: init + user script
    String fullScript = initScript + "\n" + script;

    // Execute with ScriptExecutor (request object is now created in script)
    ScriptExecutor.ScriptExecutionResult result = scriptExecutor.execute(fullScript, null);

    // Handle execution result
    if (!result.isSuccess()) {
      return createErrorResponse(
          result.getStatusCode(), result.getError(), result.getMessage(), result.getThrowable());
    }

    // Call httpHandler if it exists
    Scriptable scope = result.getScope();
    Object httpHandler = scope.get("httpHandler", scope);
    if (httpHandler instanceof org.mozilla.javascript.Function) {
      org.mozilla.javascript.Function func = (org.mozilla.javascript.Function) httpHandler;
      Object requestObj = scope.get("request", scope);
      Object responseObj = scope.get("response", scope);

      Context cx = Context.enter();
      try {
        func.call(cx, scope, scope, new Object[] {requestObj, responseObj});
      } catch (org.mozilla.javascript.RhinoException e) {
        // JavaScript error thrown in httpHandler - return 400
        return createErrorResponse(
            400, "Script Error", "JavaScript execution failed: " + e.getMessage(), e);
      } finally {
        Context.exit();
      }
    }

    // Extract response data
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
          "headers",
          scriptExecutor.convertToJavaObject(
              responseScriptable.get("headers", responseScriptable)));
      response.put(
          "body",
          scriptExecutor.convertToJavaObject(responseScriptable.get("body", responseScriptable)));
    } else {
      response.put("status", 200);
      response.put("headers", new HashMap<String, String>());
      response.put("body", "");
    }

    return response;
  }

  /** Create an error response map. */
  private Map<String, Object> createErrorResponse(
      int status, String error, String message, Throwable throwable) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", status);
    response.put("headers", new HashMap<String, String>());

    ScriptExecutor.ScriptExecutionResult errorResult =
        ScriptExecutor.ScriptExecutionResult.error(status, error, message, throwable);
    response.put("body", errorResult.toErrorJson());

    return response;
  }
}
