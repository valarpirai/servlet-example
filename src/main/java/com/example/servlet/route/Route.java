package com.example.servlet.route;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Represents a single route configuration.
 */
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
  private Pattern pathPattern;

  // Getters and setters
  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
    this.pathPattern = compilePathPattern(path);
  }

  public List<String> getMethods() {
    return methods;
  }

  public void setMethods(List<String> methods) {
    this.methods = methods;
  }

  public void setMethod(String method) {
    this.methods = List.of(method);
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getHandler() {
    return handler;
  }

  public void setHandler(String handler) {
    this.handler = handler;
  }

  public String getHandlerMethod() {
    return handlerMethod;
  }

  public void setHandlerMethod(String handlerMethod) {
    this.handlerMethod = handlerMethod;
  }

  public String getProcessor() {
    return processor;
  }

  public void setProcessor(String processor) {
    this.processor = processor;
  }

  public String getResource() {
    return resource;
  }

  public void setResource(String resource) {
    this.resource = resource;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<String> getPathParams() {
    return pathParams;
  }

  public void setPathParams(List<String> pathParams) {
    this.pathParams = pathParams;
  }

  public Pattern getPathPattern() {
    return pathPattern;
  }

  /**
   * Check if this route matches the given method and path.
   */
  public boolean matches(String method, String requestPath) {
    if (!methods.contains(method)) {
      return false;
    }
    return pathPattern.matcher(requestPath).matches();
  }

  /**
   * Extract path parameters from the request path.
   */
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
   * Compile path pattern with parameter placeholders into regex.
   * /api/attachment/{id} → /api/attachment/([^/]+)
   * /api/modules/** → /api/modules/.*
   */
  private Pattern compilePathPattern(String path) {
    String regex = path
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
