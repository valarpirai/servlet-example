package com.example.processor;

import com.example.exception.ScriptExecutionException;
import com.example.model.ProcessorResponse;
import com.example.module.ModuleManager;
import com.example.script.ScriptExecutor;
import com.example.script.ScriptModuleLoader;
import com.example.script.ScriptScopeBuilder;
import com.example.security.ScriptSecurityManager;
import com.example.util.JsonConverter;
import com.example.util.JsonUtil;
import com.example.util.RequestHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class ScriptProcessor implements IRequestProcessor {

  private final ScriptExecutor scriptExecutor = new ScriptExecutor();
  private final ScriptScopeBuilder scopeBuilder = new ScriptScopeBuilder();
  private final ScriptModuleLoader moduleLoader =
      new ScriptModuleLoader(ModuleManager.getInstance());

  @Override
  public ProcessorResponse process(HttpServletRequest request)
      throws IOException, ServletException {
    try {
      String requestBody = RequestHelper.readRequestBody(request);
      if (requestBody == null || requestBody.trim().isEmpty()) {
        return error(400, "Bad Request", "Empty request body");
      }

      JsonObject json = JsonParser.parseString(requestBody).getAsJsonObject();
      if (!json.has("script")) {
        return error(400, "Bad Request", "Missing 'script' field");
      }

      String script = json.get("script").getAsString();
      Map<String, Object> params = extractParams(json);
      List<String> consoleLogs = new ArrayList<>();

      Runtime runtime = Runtime.getRuntime();
      long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
      long startTime = System.currentTimeMillis();

      Object result = executeScript(script, params, request, consoleLogs, startTime, memoryBefore);

      long executionTimeMs = System.currentTimeMillis() - startTime;
      long memoryUsedBytes = (runtime.totalMemory() - runtime.freeMemory()) - memoryBefore;

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("result", result);
      responseData.put("executionTimeMs", executionTimeMs);
      responseData.put("memoryUsedBytes", memoryUsedBytes);
      if (!consoleLogs.isEmpty()) {
        responseData.put("console", consoleLogs);
      }

      return ProcessorResponse.builder()
          .statusCode(200)
          .body(JsonUtil.successResponse(responseData))
          .build();

    } catch (Error e) {
      if (e.getMessage() != null && e.getMessage().contains("timeout exceeded")) {
        return error(408, "Request Timeout", e.getMessage());
      }
      if (e.getMessage() != null && e.getMessage().contains("memory limit exceeded")) {
        return error(413, "Payload Too Large", e.getMessage());
      }
      return error(500, "Internal Server Error", "Script execution error: " + e.getMessage());
    } catch (org.mozilla.javascript.RhinoException e) {
      return error(400, "Script Error", "JavaScript execution failed: " + e.getMessage());
    } catch (Exception e) {
      return error(500, "Internal Server Error", "Error processing script: " + e.getMessage());
    }
  }

  private Map<String, Object> extractParams(JsonObject json) {
    Map<String, Object> params = new HashMap<>();
    if (json.has("params") && json.get("params").isJsonObject()) {
      json.getAsJsonObject("params")
          .entrySet()
          .forEach(e -> params.put(e.getKey(), JsonConverter.convertJsonElement(e.getValue())));
    }
    return params;
  }

  private Object executeScript(
      String script,
      Map<String, Object> params,
      HttpServletRequest request,
      List<String> consoleLogs,
      long startTime,
      long memoryBefore) {

    ScriptSecurityManager.SecureContextFactory factory =
        ScriptSecurityManager.createSecureContextFactory(startTime, memoryBefore);

    Context cx = factory.enterContext();
    try {
      cx.setOptimizationLevel(ScriptSecurityManager.getOptimizationLevel());
      cx.setInstructionObserverThreshold(ScriptSecurityManager.getInstructionThreshold());

      Scriptable scope = cx.initStandardObjects();
      scopeBuilder.populate(scope, params, request, consoleLogs);

      String preparedScript;
      try {
        preparedScript = moduleLoader.prepareWithModules(script);
      } catch (IOException e) {
        throw new ScriptExecutionException("Module loading failed: " + e.getMessage(), e);
      }

      Object result = cx.evaluateString(scope, preparedScript, "userScript", 1, null);
      return scriptExecutor.convertToJavaObject(result);

    } catch (Error e) {
      if (e.getMessage() != null
          && (e.getMessage().contains("timeout exceeded")
              || e.getMessage().contains("memory limit exceeded"))) {
        throw e;
      }
      throw new ScriptExecutionException("Script execution error: " + e.getMessage(), e);
    } finally {
      Context.exit();
    }
  }

  private ProcessorResponse error(int status, String error, String message) {
    return ProcessorResponse.builder()
        .statusCode(status)
        .body(JsonUtil.errorResponse(error, message, status))
        .build();
  }
}
