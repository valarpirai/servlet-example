package com.example.servlet.script;

import com.example.servlet.security.ScriptSecurityManager;
import com.example.servlet.util.JsonUtil;
import com.example.servlet.util.PropertiesUtil;
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
 * Shared script execution engine for JavaScript code using Mozilla Rhino. Provides secure script
 * execution with timeout, memory limits, and module loading via require().
 */
public class ScriptExecutor {

  private final Map<String, String> scriptCache = new HashMap<>();

  /**
   * Execute JavaScript code with the given scope setup.
   *
   * @param script JavaScript code to execute
   * @param scopeSetup Callback to setup scope variables before execution
   * @return Execution result
   */
  public ScriptExecutionResult execute(String script, ScopeSetup scopeSetup) {
    Runtime runtime = Runtime.getRuntime();
    long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
    long startTime = System.currentTimeMillis();

    ScriptSecurityManager.SecureContextFactory factory =
        ScriptSecurityManager.createSecureContextFactory(startTime, memoryBefore);

    Context cx = factory.enterContext();

    try {
      cx.setOptimizationLevel(ScriptSecurityManager.getOptimizationLevel());
      cx.setInstructionObserverThreshold(ScriptSecurityManager.getInstructionThreshold());

      Scriptable scope = cx.initStandardObjects();

      // Add require() function for module loading
      addRequireFunction(scope, cx);

      // Let caller setup scope variables
      if (scopeSetup != null) {
        scopeSetup.setup(scope, cx);
      }

      // Execute the script
      Object result = cx.evaluateString(scope, script, "script", 1, null);

      // Convert result to Java object
      return ScriptExecutionResult.success(convertToJavaObject(result), scope);

    } catch (org.mozilla.javascript.RhinoException e) {
      return ScriptExecutionResult.error(
          400, "Script Error", "JavaScript execution failed: " + e.getMessage(), e);
    } catch (Error e) {
      if (e.getMessage() != null && e.getMessage().contains("timeout exceeded")) {
        return ScriptExecutionResult.error(408, "Request Timeout", e.getMessage(), e);
      } else if (e.getMessage() != null && e.getMessage().contains("memory limit exceeded")) {
        return ScriptExecutionResult.error(413, "Payload Too Large", e.getMessage(), e);
      }
      return ScriptExecutionResult.error(
          500, "Internal Server Error", "Script execution error: " + e.getMessage(), e);
    } catch (Exception e) {
      return ScriptExecutionResult.error(
          500, "Internal Server Error", "Error executing script: " + e.getMessage(), e);
    } finally {
      Context.exit();
    }
  }

  /**
   * Load a script file from the file system with caching support.
   *
   * @param scriptPath Relative path from project root
   * @return Script content or null if not found
   */
  public String loadScript(String scriptPath) throws IOException {
    // Check cache first
    if (scriptCache.containsKey(scriptPath)) {
      return scriptCache.get(scriptPath);
    }

    Path path = Paths.get(scriptPath);
    if (!Files.exists(path)) {
      return null;
    }

    String content = Files.readString(path);

    // Cache the script in production mode
    if (!PropertiesUtil.isDevEnvironment()) {
      scriptCache.put(scriptPath, content);
    }

    return content;
  }

  /** Add require() function to the scope for loading modules. */
  private void addRequireFunction(Scriptable scope, Context cx) {
    String requireFunction =
        """
        var __loadedModules = {};

        function require(modulePath) {
          var normalizedPath = modulePath;

          if (modulePath.startsWith('../')) {
            normalizedPath = modulePath.substring(3);
          } else if (modulePath.startsWith('./')) {
            normalizedPath = modulePath.substring(2);
          }

          if (__loadedModules[normalizedPath]) {
            return __loadedModules[normalizedPath];
          }

          var content = __loadScriptFile(normalizedPath);
          if (!content) {
            throw new Error('Module not found: ' + modulePath);
          }

          var module = { exports: {} };
          var exports = module.exports;

          eval(content);

          __loadedModules[normalizedPath] = module.exports;

          return module.exports;
        }
        """;

    cx.evaluateString(scope, requireFunction, "require", 1, null);

    // Add Java function to load script files
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

            // Search in lib, vendor directories
            String[] searchPaths = {"scripts/lib/" + modulePath, "scripts/vendor/" + modulePath};

            for (String searchPath : searchPaths) {
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

  /** Convert JavaScript object to Java object. */
  public Object convertToJavaObject(Object obj) {
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

  /** Functional interface for setting up scope before script execution. */
  @FunctionalInterface
  public interface ScopeSetup {
    void setup(Scriptable scope, Context context);
  }

  /** Result of script execution. */
  public static class ScriptExecutionResult {
    private final boolean success;
    private final Object result;
    private final Scriptable scope;
    private final int statusCode;
    private final String error;
    private final String message;
    private final Throwable throwable;

    private ScriptExecutionResult(
        boolean success,
        Object result,
        Scriptable scope,
        int statusCode,
        String error,
        String message,
        Throwable throwable) {
      this.success = success;
      this.result = result;
      this.scope = scope;
      this.statusCode = statusCode;
      this.error = error;
      this.message = message;
      this.throwable = throwable;
    }

    public static ScriptExecutionResult success(Object result, Scriptable scope) {
      return new ScriptExecutionResult(true, result, scope, 200, null, null, null);
    }

    public static ScriptExecutionResult error(
        int statusCode, String error, String message, Throwable throwable) {
      return new ScriptExecutionResult(false, null, null, statusCode, error, message, throwable);
    }

    public boolean isSuccess() {
      return success;
    }

    public Object getResult() {
      return result;
    }

    public Scriptable getScope() {
      return scope;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public String getError() {
      return error;
    }

    public String getMessage() {
      return message;
    }

    public Throwable getThrowable() {
      return throwable;
    }

    /** Convert error result to JSON response. */
    public String toErrorJson() {
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

      return JsonUtil.toJson(errorBody);
    }
  }
}
