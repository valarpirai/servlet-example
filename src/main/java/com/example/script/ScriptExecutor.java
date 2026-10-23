package com.example.script;

import com.example.record.RecordConstructor;
import com.example.security.ScriptSecurityManager;
import com.example.util.JsonUtil;
import com.example.util.PropertiesUtil;
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

      // Add the TableRecord constructor for table access
      ScriptableObject.putProperty(scope, "TableRecord", new RecordConstructor());

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

  /** Convert JavaScript object to Java object. */
  public Object convertToJavaObject(Object obj) {
    if (obj == null || obj == org.mozilla.javascript.Undefined.instance) {
      return null;
    }

    // Handle JavaScript primitives first
    if (obj instanceof Number || obj instanceof String || obj instanceof Boolean) {
      return obj;
    }

    // Handle JavaScript arrays
    if (obj instanceof org.mozilla.javascript.NativeArray) {
      org.mozilla.javascript.NativeArray array = (org.mozilla.javascript.NativeArray) obj;
      Object[] result = new Object[(int) array.getLength()];
      for (int i = 0; i < array.getLength(); i++) {
        result[i] = convertToJavaObject(array.get(i));
      }
      return result;
    }

    // Handle JavaScript objects
    if (obj instanceof org.mozilla.javascript.NativeObject) {
      org.mozilla.javascript.NativeObject nativeObj = (org.mozilla.javascript.NativeObject) obj;
      Map<String, Object> result = new HashMap<>();
      for (Object key : nativeObj.keySet()) {
        result.put(key.toString(), convertToJavaObject(nativeObj.get(key)));
      }
      return result;
    }

    // Handle NativeJavaObject (wrapper around Java objects created from JavaScript)
    if (obj instanceof org.mozilla.javascript.NativeJavaObject) {
      // Unwrap the Java object
      return ((org.mozilla.javascript.NativeJavaObject) obj).unwrap();
    }

    // Handle generic Scriptable objects (but not NativeJavaObject which we handled above)
    if (obj instanceof org.mozilla.javascript.Scriptable) {
      // Check if it's a wrapper around a Java object
      if (obj instanceof org.mozilla.javascript.Wrapper) {
        return ((org.mozilla.javascript.Wrapper) obj).unwrap();
      }

      // Otherwise convert to Map
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

    // For everything else (already a Java object), return as-is
    return obj;
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
