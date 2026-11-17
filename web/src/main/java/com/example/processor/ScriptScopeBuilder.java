package com.example.processor;

import com.example.record.BusinessRules;
import com.example.record.RecordConstructor;
import com.example.security.ScriptGlobals;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/** Populates a Rhino scope with server-side context objects and utility functions. */
public class ScriptScopeBuilder {

  /**
   * Adds params, a {@code request} global (method, path, queryParams), a {@code console.log}
   * capture function, and the {@code TableRecord} constructor and the {@code BusinessRules} manager
   * to the given scope.
   */
  public void populate(
      Scriptable scope,
      Map<String, Object> params,
      HttpServletRequest request,
      List<String> consoleLogs) {

    addParams(scope, params);
    addRequestContext(scope, request);
    addConsole(scope, consoleLogs);
    ScriptableObject.putProperty(scope, "TableRecord", new RecordConstructor());
    ScriptableObject.putProperty(
        scope, "BusinessRules", Context.javaToJS(new BusinessRules(), scope));
    ScriptGlobals.apply(scope);
  }

  private void addParams(Scriptable scope, Map<String, Object> params) {
    if (params == null) return;
    params.forEach((key, value) -> ScriptableObject.putProperty(scope, key, value));
  }

  private void addRequestContext(Scriptable scope, HttpServletRequest request) {
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("method", request.getMethod());
    ctx.put("path", request.getPathInfo());
    ctx.put("remoteAddr", request.getRemoteAddr());

    Map<String, String> queryParams = new HashMap<>();
    if (request.getQueryString() != null) {
      request
          .getParameterMap()
          .forEach((key, values) -> queryParams.put(key, values.length > 0 ? values[0] : ""));
    }
    ctx.put("queryParams", queryParams);

    ScriptableObject.putProperty(scope, "request", Context.javaToJS(ctx, scope));
  }

  private void addConsole(Scriptable scope, List<String> consoleLogs) {
    Context cx = Context.getCurrentContext();
    ScriptableObject.putProperty(scope, "__consoleLogs", Context.javaToJS(consoleLogs, scope));

    cx.evaluateString(
        scope,
        "var console = { log: function() {"
            + "  __consoleLogs.add(Array.prototype.slice.call(arguments).join(' '));"
            + "} };",
        "console",
        1,
        null);
  }
}
