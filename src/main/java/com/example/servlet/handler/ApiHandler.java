package com.example.servlet.handler;

import com.example.servlet.processor.ApiScriptProcessor;
import com.example.servlet.util.ResponseHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for scripted REST API endpoints. Routes requests to JavaScript handlers in scripts/api/
 * directory.
 */
public class ApiHandler {

  private static final Logger logger = LoggerFactory.getLogger(ApiHandler.class);
  private static ApiHandler instance;
  private final ApiScriptProcessor processor;

  private ApiHandler() {
    this.processor = new ApiScriptProcessor();
  }

  public static synchronized ApiHandler getInstance() {
    if (instance == null) {
      instance = new ApiHandler();
    }
    return instance;
  }

  /**
   * Handle API request. Extracts endpoint name from path and delegates to ApiScriptProcessor.
   *
   * @param request HTTP request
   * @param response HTTP response
   */
  public void handleRequest(HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    try {
      // Extract endpoint name from path
      // Expected format: /api/v1/{endpoint-name}
      String pathInfo = request.getPathInfo();
      String endpointName = extractEndpointName(pathInfo);

      if (endpointName == null || endpointName.isEmpty()) {
        ResponseHelper.sendBadRequest(response, "Invalid API endpoint");
        return;
      }

      // Extract request data
      String method = request.getMethod();
      String path = request.getRequestURI();
      Map<String, String> queryParams = extractQueryParams(request);
      Map<String, String> headers = extractHeaders(request);
      JsonObject body = extractBody(request);

      logger.info("API request: {} {} (endpoint: {})", method, path, endpointName);

      // Process the request
      Map<String, Object> result =
          processor.processRequest(endpointName, method, path, queryParams, headers, body);

      // Send response
      sendResponse(response, result);

    } catch (Exception e) {
      logger.error("Error handling API request", e);
      ResponseHelper.sendJsonError(
          response,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Internal Server Error",
          "Error processing API request: " + e.getMessage());
    }
  }

  /**
   * Extract endpoint name from path. Expected format: /api/v1/{endpoint-name} or just
   * /{endpoint-name}
   */
  private String extractEndpointName(String pathInfo) {
    if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
      return null;
    }

    // Remove leading slash
    String path = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;

    // Split by slash and get the last segment
    String[] segments = path.split("/");
    if (segments.length == 0) {
      return null;
    }

    // Return the last segment as endpoint name
    return segments[segments.length - 1];
  }

  /** Extract query parameters from request. */
  private Map<String, String> extractQueryParams(HttpServletRequest request) {
    Map<String, String> params = new HashMap<>();
    if (request.getQueryString() != null) {
      request
          .getParameterMap()
          .forEach(
              (key, values) -> {
                params.put(key, values.length > 0 ? values[0] : "");
              });
    }
    return params;
  }

  /** Extract headers from request. */
  private Map<String, String> extractHeaders(HttpServletRequest request) {
    Map<String, String> headers = new HashMap<>();
    java.util.Enumeration<String> headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      headers.put(headerName, request.getHeader(headerName));
    }
    return headers;
  }

  /** Extract and parse JSON body from request. */
  private JsonObject extractBody(HttpServletRequest request) throws IOException {
    // Only parse body for methods that typically have a body
    String method = request.getMethod();
    if (!method.equals("POST") && !method.equals("PUT") && !method.equals("PATCH")) {
      return new JsonObject();
    }

    // Read body
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = request.getReader()) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
    }

    String bodyString = sb.toString();
    if (bodyString.isEmpty()) {
      return new JsonObject();
    }

    // Parse JSON
    try {
      return JsonParser.parseString(bodyString).getAsJsonObject();
    } catch (Exception e) {
      logger.warn("Failed to parse request body as JSON: {}", e.getMessage());
      return new JsonObject();
    }
  }

  /** Send response based on processor result. */
  @SuppressWarnings("unchecked")
  private void sendResponse(HttpServletResponse response, Map<String, Object> result)
      throws IOException {

    // Extract status
    int status = (int) result.getOrDefault("status", 200);
    response.setStatus(status);

    // Extract and set headers
    Map<String, String> headers =
        (Map<String, String>) result.getOrDefault("headers", new HashMap<>());
    for (Map.Entry<String, String> header : headers.entrySet()) {
      response.setHeader(header.getKey(), header.getValue());
    }

    // Extract and send body
    String body = (String) result.getOrDefault("body", "");

    // Set content type if not already set
    if (response.getContentType() == null) {
      response.setContentType("application/json");
    }

    response.getWriter().write(body);
    response.getWriter().flush();
  }
}
