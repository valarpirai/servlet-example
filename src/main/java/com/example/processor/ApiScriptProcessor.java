package com.example.processor;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.module.ModuleManager;
import com.example.script.ScriptExecutor;
import com.example.script.ScriptModuleLoader;
import com.example.util.JsonConverter;
import com.example.util.JsonUtil;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * Processor for scripted REST API endpoints. Loads each endpoint script from the {@code
 * api_scripts} table and executes it with request/response handling. Supports require() for shared
 * libraries stored in the {@code modules} table as lib/ and vendor/ modules.
 */
public class ApiScriptProcessor {

  private static final Table<Record> API_SCRIPTS = table("api_scripts");
  private static final Field<String> NAME = field("name", String.class);
  private static final Field<String> CONTENT = field("content", String.class);

  private final Database database;
  private final ScriptExecutor scriptExecutor = new ScriptExecutor();
  private final ScriptModuleLoader moduleLoader;

  /** Creates a processor on the application database, looked up on first use. */
  public ApiScriptProcessor() {
    this.database = null;
    this.moduleLoader = new ScriptModuleLoader(ModuleManager.getInstance());
  }

  /**
   * Creates a processor on a fixed database.
   *
   * @param database database holding api_scripts and modules
   */
  public ApiScriptProcessor(Database database) {
    this.database = database;
    this.moduleLoader = new ScriptModuleLoader(new ModuleManager(database));
  }

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
      String scriptContent = loadEndpoint(endpointName);

      if (scriptContent == null) {
        return createErrorResponse(
            404, "Not Found", "Endpoint script not found: " + endpointName, null);
      }

      // Resolve import statements, then execute
      String prepared = moduleLoader.prepareWithModules(scriptContent);
      return executeScript(prepared, method, path, queryParams, headers, body);

    } catch (Exception e) {
      return createErrorResponse(
          500, "Internal Server Error", "Error processing API request: " + e.getMessage(), e);
    }
  }

  /** Reads one endpoint script. Returns null when no row has that name. */
  private String loadEndpoint(String endpointName) throws Exception {
    Database db = database != null ? database : DbPropertiesLoader.getDatabase();
    if (db == null) {
      throw new IOException("API scripts are stored in the database, which is not ready");
    }
    return db.query(
        dsl ->
            dsl.select(CONTENT).from(API_SCRIPTS).where(NAME.eq(endpointName)).fetchOne(CONTENT));
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
