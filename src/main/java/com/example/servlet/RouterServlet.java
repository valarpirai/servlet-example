package com.example.servlet;

import com.example.servlet.route.RouteDispatcher;
import com.example.servlet.route.RouteRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouterServlet extends HttpServlet {

  private static final Logger logger = LoggerFactory.getLogger(RouterServlet.class);
  private AtomicLong requestCount;
  private long startTime;
  private RouteDispatcher routeDispatcher;
  private static final DateTimeFormatter LOG_DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

  @Override
  public void init() throws ServletException {
    super.init();
    startTime = System.currentTimeMillis();
    requestCount = new AtomicLong(0);

    // Initialize route registry and dispatcher
    RouteRegistry.getInstance(); // Triggers routes.yml loading
    routeDispatcher = new RouteDispatcher();
  }

  /** Functional interface for request handlers */
  @FunctionalInterface
  private interface RequestHandler {
    void handle() throws ServletException, IOException;
  }

  /** Common request handling wrapper with timing and logging */
  private void handleRequest(
      HttpServletRequest request, HttpServletResponse response, RequestHandler handler)
      throws ServletException, IOException {
    long startTime = System.currentTimeMillis();
    requestCount.incrementAndGet();

    try {
      handler.handle();
      long responseTime = System.currentTimeMillis() - startTime;
      logRequest(request, response.getStatus(), responseTime, 0);
    } catch (Exception e) {
      long responseTime = System.currentTimeMillis() - startTime;
      logError(request, e, responseTime);
      throw e;
    }
  }

  /** Dispatch request via routes.yml or return 404 */
  private void dispatchOrNotFound(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String path = request.getPathInfo();
    if (path == null) path = "/";

    RouteRegistry.RouteMatch match =
        RouteRegistry.getInstance().findRoute(request.getMethod(), path);

    if (match != null && routeDispatcher.dispatch(match, request, response)) {
      return; // Route handled successfully
    }

    // Handle builtin routes (dispatcher returns false for these)
    if (match != null && "builtin".equals(match.getRoute().getType())) {
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      PrintWriter out = response.getWriter();

      switch (path) {
        case "/health":
          handleHealth(out);
          break;
        case "/metrics":
          handleMetrics(out);
          break;
      }

      out.flush();
      return;
    }

    // No route found - return 404
    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    PrintWriter out = response.getWriter();
    out.print(String.format("{\"error\":\"Not Found\",\"path\":\"%s\",\"status\":404}", path));
    out.flush();
  }

  /** Build full URI with query string */
  private String buildFullUri(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String queryString = request.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      uri = uri + "?" + queryString;
    }
    return uri;
  }

  private void logRequest(
      HttpServletRequest request, int statusCode, long responseTimeMs, long responseSize) {
    // Apache/Nginx style access log format
    String remoteAddr = request.getRemoteAddr();
    String timestamp = ZonedDateTime.now().format(LOG_DATE_FORMAT);
    String method = request.getMethod();
    String uri = buildFullUri(request);
    String protocol = request.getProtocol();
    String referer = request.getHeader("Referer");
    if (referer == null) referer = "-";
    String userAgent = request.getHeader("User-Agent");
    if (userAgent == null) userAgent = "-";
    String contentType = request.getContentType();
    if (contentType == null) contentType = "-";

    String logMessage =
        String.format(
            "%s - - [%s] \"%s %s %s\" %d %d \"%s\" \"%s\" %dms [%s]",
            remoteAddr,
            timestamp,
            method,
            uri,
            protocol,
            statusCode,
            responseSize,
            referer,
            userAgent,
            responseTimeMs,
            contentType);

    logger.info(logMessage);
  }

  private void logError(HttpServletRequest request, Throwable exception, long responseTimeMs) {
    // Log unhandled exceptions with full stack trace
    String remoteAddr = request.getRemoteAddr();
    String timestamp = ZonedDateTime.now().format(LOG_DATE_FORMAT);
    String method = request.getMethod();
    String uri = buildFullUri(request);

    String errorMessage =
        String.format(
            "%s - [%s] \"%s %s\" - %s: %s - Response Time: %dms",
            remoteAddr,
            timestamp,
            method,
            uri,
            exception.getClass().getName(),
            exception.getMessage(),
            responseTimeMs);

    logger.error(errorMessage, exception);
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    handleRequest(request, response, () -> dispatchOrNotFound(request, response));
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    handleRequest(request, response, () -> dispatchOrNotFound(request, response));
  }

  @Override
  protected void doPut(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    handleRequest(request, response, () -> dispatchOrNotFound(request, response));
  }

  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    handleRequest(request, response, () -> dispatchOrNotFound(request, response));
  }

  /** Handle /health endpoint - called for builtin routes */
  private void handleHealth(PrintWriter out) {
    long uptime = System.currentTimeMillis() - startTime;

    String healthStatus =
        String.format(
            "{\"status\":\"UP\",\"timestamp\":%d,\"uptime\":\"%d ms\"}",
            System.currentTimeMillis(), uptime);

    out.print(healthStatus);
  }

  /** Handle /metrics endpoint - called for builtin routes */
  private void handleMetrics(PrintWriter out) {
    Runtime runtime = Runtime.getRuntime();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long usedMemory = totalMemory - freeMemory;
    long maxMemory = runtime.maxMemory();

    String metrics =
        String.format(
            "{\"metrics\":{"
                + "\"totalRequests\":%d,"
                + "\"memory\":{"
                + "\"used\":%d,"
                + "\"free\":%d,"
                + "\"total\":%d,"
                + "\"max\":%d"
                + "},"
                + "\"threads\":{"
                + "\"active\":%d"
                + "},"
                + "\"timestamp\":%d"
                + "}}",
            requestCount.get(),
            usedMemory,
            freeMemory,
            totalMemory,
            maxMemory,
            Thread.activeCount(),
            System.currentTimeMillis());

    out.print(metrics);
  }
}
