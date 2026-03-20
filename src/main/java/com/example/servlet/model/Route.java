package com.example.servlet.model;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Represents a single route configuration. */
@Getter
@Setter
public class Route {

  private String path;
  private List<String> methods;
  private String type; // static, handler, processor, builtin
  private String handler;
  private String handlerMethod;
  private String processor;
  private String resource;
  private String contentType;
  private String description;
  private List<String> pathParams;

  @Getter(lombok.AccessLevel.NONE)
  @Setter(lombok.AccessLevel.NONE)
  private Pattern pathPattern;

  /** Custom setter for path that also compiles the pattern. */
  public void setPath(String path) {
    this.path = path;
    this.pathPattern = compilePathPattern(path);
  }

  /** Get the compiled path pattern. */
  public Pattern getPathPattern() {
    return pathPattern;
  }

  /** Convenience setter for single method. */
  public void setMethod(String method) {
    this.methods = List.of(method);
  }

  /** Check if this route matches the given method and path. */
  public boolean matches(String method, String requestPath) {
    if (!methods.contains(method)) {
      return false;
    }
    return pathPattern.matcher(requestPath).matches();
  }

  /** Extract path parameters from the request path. */
  public Map<String, String> extractPathParams(String requestPath) {
    if (pathParams == null || pathParams.isEmpty()) {
      return Map.of();
    }

    java.util.regex.Matcher matcher = pathPattern.matcher(requestPath);
    if (!matcher.matches()) {
      return Map.of();
    }

    Map<String, String> params = new java.util.HashMap<>();
    for (int i = 0; i < pathParams.size(); i++) {
      params.put(pathParams.get(i), matcher.group(i + 1));
    }
    return params;
  }

  /**
   * Compile path pattern with parameter placeholders into regex. /api/attachment/{id} →
   * /api/attachment/([^/]+) /api/modules/** → /api/modules/.*
   */
  private Pattern compilePathPattern(String path) {
    String regex =
        path
            // Escape special regex characters except {, }, *
            .replaceAll("([.+?^$|()\\[\\]\\\\])", "\\\\$1")
            // Replace {param} with capture group
            .replaceAll("\\{([^}]+)\\}", "([^/]+)")
            // Replace /** with wildcard
            .replaceAll("/\\*\\*", "/.*");

    return Pattern.compile("^" + regex + "$");
  }

  @Override
  public String toString() {
    return String.format(
        "Route{methods=%s, path='%s', type='%s', handler='%s', description='%s'}",
        methods, path, type, handler, description);
  }
}
