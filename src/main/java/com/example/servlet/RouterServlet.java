package com.example.servlet;

import com.example.servlet.handler.AttachmentHandler;
import com.example.servlet.handler.DataBrowserHandler;
import com.example.servlet.processor.FileUploadProcessor;
import com.example.servlet.processor.FormDataProcessor;
import com.example.servlet.processor.JsonDataProcessor;
import com.example.servlet.processor.ModuleProcessor;
import com.example.servlet.processor.ProcessorRegistry;
import com.example.servlet.processor.ProcessorResponse;
import com.example.servlet.processor.RequestProcessor;
import com.example.servlet.processor.ScriptProcessor;
import com.example.servlet.processor.TemplateProcessor;
import com.example.servlet.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouterServlet extends HttpServlet {

  private static final Logger logger = LoggerFactory.getLogger(RouterServlet.class);
  private AtomicLong requestCount;
  private long startTime;
  private static final DateTimeFormatter LOG_DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

  @Override
  public void init() throws ServletException {
    super.init();
    startTime = System.currentTimeMillis();
    requestCount = new AtomicLong(0);

    // Register request processors
    ProcessorRegistry registry = ProcessorRegistry.getInstance();
    registry.register(new FormDataProcessor());
    registry.register(new JsonDataProcessor());
    registry.register(new FileUploadProcessor());
    registry.register(new ScriptProcessor());
    registry.register(new TemplateProcessor());
  }

  private void logRequest(
      HttpServletRequest request, int statusCode, long responseTimeMs, long responseSize) {
    // Apache/Nginx style access log format
    // IP - - [timestamp] "METHOD PATH PROTOCOL" STATUS SIZE "REFERER" "USER-AGENT" response_time_ms
    String remoteAddr = request.getRemoteAddr();
    String timestamp = ZonedDateTime.now().format(LOG_DATE_FORMAT);
    String method = request.getMethod();
    String uri = request.getRequestURI();
    String queryString = request.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      uri = uri + "?" + queryString;
    }
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
    String uri = request.getRequestURI();
    String queryString = request.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      uri = uri + "?" + queryString;
    }

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

    long startTime = System.currentTimeMillis();
    requestCount.incrementAndGet();

    try {
      String path = request.getPathInfo();
      if (path == null) {
        path = "/";
      }

      // Handle static files first (they use OutputStream)
      if ("/script-editor".equals(path)) {
        serveStaticFile(request, response, "static/script-editor.html", "text/html");
        long responseTime = System.currentTimeMillis() - startTime;
        logRequest(request, response.getStatus(), responseTime, 0); // Size unknown for static files
        return;
      }

      if ("/data-browser".equals(path)) {
        serveStaticFile(request, response, "static/data-browser.html", "text/html");
        long responseTime = System.currentTimeMillis() - startTime;
        logRequest(request, response.getStatus(), responseTime, 0);
        return;
      }

      // Handle module API requests
      if (path != null && path.startsWith("/api/modules")) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        handleModulesRequest(request, response);
        long responseTime = System.currentTimeMillis() - startTime;
        logRequest(request, response.getStatus(), responseTime, 0);
        return;
      }

      // Handle attachment list request
      if (path != null && path.equals("/api/attachments")) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        AttachmentHandler.getInstance().handleList(response);
        long responseTime = System.currentTimeMillis() - startTime;
        logRequest(request, response.getStatus(), responseTime, 0);
        return;
      }

      // Handle attachment download requests
      if (path != null && path.startsWith("/api/attachment/")) {
        String[] parts = path.split("/");
        if (parts.length >= 4) {
          String attachmentId = parts[3];
          String action = parts.length > 4 ? parts[4] : "metadata";

          if ("download".equals(action)) {
            AttachmentHandler.getInstance().handleDownload(request, response, attachmentId);
          } else {
            AttachmentHandler.getInstance().handleMetadata(response, attachmentId);
          }

          long responseTime = System.currentTimeMillis() - startTime;
          logRequest(request, response.getStatus(), responseTime, 0);
          return;
        }
      }

      // For JSON responses, set content type and get writer
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
        case "/":
          handleRoot(response, out);
          break;
        default:
          handleNotFound(response, out, path);
          break;
      }

      out.flush();

      long responseTime = System.currentTimeMillis() - startTime;
      logRequest(request, response.getStatus(), responseTime, 0); // Size in bytes (approximate)
    } catch (Exception e) {
      long responseTime = System.currentTimeMillis() - startTime;
      logError(request, e, responseTime);
      throw e;
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    long startTime = System.currentTimeMillis();
    requestCount.incrementAndGet();

    try {
      String path = request.getPathInfo();
      if (path == null) {
        path = "/";
      }

      // Set default response content type
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");

      // Route based on path for POST requests
      switch (path) {
        case "/api/form":
        case "/api/json":
        case "/api/upload":
        case "/api/script":
        case "/api/render":
          handleProcessorRequest(request, response);
          break;
        default:
          if (path != null && path.startsWith("/api/modules")) {
            handleModulesRequest(request, response);
            break;
          }
          switch (path) {
            case "/api/data-browser/driver-status":
              DataBrowserHandler.getInstance().handleDriverStatus(request, response);
              break;
            case "/api/data-browser/download":
              DataBrowserHandler.getInstance().handleDownload(request, response);
              break;
            case "/api/data-browser/connect":
              DataBrowserHandler.getInstance().handleConnect(request, response);
              break;
            case "/api/data-browser/tables":
              DataBrowserHandler.getInstance().handleTables(request, response);
              break;
            case "/api/data-browser/query":
              DataBrowserHandler.getInstance().handleQuery(request, response);
              break;
            case "/api/data-browser/disconnect":
              DataBrowserHandler.getInstance().handleDisconnect(request, response);
              break;
            default:
              PrintWriter out = response.getWriter();
              handleNotFound(response, out, path);
              out.flush();
              break;
          }
          break;
      }

      long responseTime = System.currentTimeMillis() - startTime;
      logRequest(request, response.getStatus(), responseTime, 0);
    } catch (Exception e) {
      long responseTime = System.currentTimeMillis() - startTime;
      logError(request, e, responseTime);
      throw e;
    }
  }

  @Override
  protected void doPut(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    long startTime = System.currentTimeMillis();
    requestCount.incrementAndGet();

    try {
      String path = request.getPathInfo();
      if (path != null && path.startsWith("/api/modules")) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        handleModulesRequest(request, response);
      } else {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        handleNotFound(response, out, path);
        out.flush();
      }

      long responseTime = System.currentTimeMillis() - startTime;
      logRequest(request, response.getStatus(), responseTime, 0);
    } catch (Exception e) {
      long responseTime = System.currentTimeMillis() - startTime;
      logError(request, e, responseTime);
      throw e;
    }
  }

  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    long startTime = System.currentTimeMillis();
    requestCount.incrementAndGet();

    try {
      String path = request.getPathInfo();

      // Handle attachment deletion
      if (path != null && path.startsWith("/api/attachment/")) {
        String[] parts = path.split("/");
        if (parts.length >= 4) {
          String attachmentId = parts[3];
          AttachmentHandler.getInstance().handleDelete(response, attachmentId);
          long responseTime = System.currentTimeMillis() - startTime;
          logRequest(request, response.getStatus(), responseTime, 0);
          return;
        }
      }

      if (path != null && path.startsWith("/api/modules")) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        handleModulesRequest(request, response);
      } else {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        handleNotFound(response, out, path);
        out.flush();
      }

      long responseTime = System.currentTimeMillis() - startTime;
      logRequest(request, response.getStatus(), responseTime, 0);
    } catch (Exception e) {
      long responseTime = System.currentTimeMillis() - startTime;
      logError(request, e, responseTime);
      throw e;
    }
  }

  private void handleModulesRequest(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    ModuleProcessor processor = new ModuleProcessor();
    ProcessorResponse processorResponse = processor.process(request);

    response.setStatus(processorResponse.getStatusCode());
    response.setContentType(processorResponse.getContentType());

    for (Map.Entry<String, String> header : processorResponse.getHeaders().entrySet()) {
      response.setHeader(header.getKey(), header.getValue());
    }

    PrintWriter out = response.getWriter();
    out.print(processorResponse.getBody());
    out.flush();
  }

  private void handleProcessorRequest(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {

    String contentType = request.getContentType();

    if (contentType == null || contentType.trim().isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      PrintWriter out = response.getWriter();
      out.print(
          JsonUtil.errorResponse(
              "Bad Request",
              "Content-Type header is required",
              HttpServletResponse.SC_BAD_REQUEST));
      out.flush();
      return;
    }

    // Get processor from registry
    ProcessorRegistry registry = ProcessorRegistry.getInstance();
    RequestProcessor processor = registry.getProcessor(contentType);

    if (processor == null) {
      response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
      PrintWriter out = response.getWriter();
      out.print(
          JsonUtil.errorResponse(
              "Unsupported Media Type",
              "No processor found for content type: " + contentType,
              HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE));
      out.flush();
      return;
    }

    // Process the request
    ProcessorResponse processorResponse = processor.process(request);

    // Write response
    response.setStatus(processorResponse.getStatusCode());
    response.setContentType(processorResponse.getContentType());

    // Set custom headers if any
    for (Map.Entry<String, String> header : processorResponse.getHeaders().entrySet()) {
      response.setHeader(header.getKey(), header.getValue());
    }

    PrintWriter out = response.getWriter();
    out.print(processorResponse.getBody());
    out.flush();
  }

  private void handleHealth(PrintWriter out) {
    long uptime = System.currentTimeMillis() - startTime;

    String healthStatus =
        String.format(
            "{\"status\":\"UP\",\"timestamp\":%d,\"uptime\":\"%d ms\"}",
            System.currentTimeMillis(), uptime);

    out.print(healthStatus);
  }

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

  private void handleRoot(HttpServletResponse response, PrintWriter out) {
    String welcomeMessage =
        String.format(
            "{\"message\":\"Welcome to Jakarta EE Servlet Application\","
                + "\"version\":\"1.0\","
                + "\"endpoints\":{"
                + "\"GET\":[\"/\",\"/health\",\"/metrics\",\"/script-editor\"],"
                + "\"POST\":[\"/api/form\",\"/api/json\",\"/api/upload\",\"/api/script\",\"/api/render\"]"
                + "},"
                + "\"timestamp\":%d}",
            System.currentTimeMillis());

    out.print(welcomeMessage);
  }

  private void handleNotFound(HttpServletResponse response, PrintWriter out, String path) {
    response.setStatus(HttpServletResponse.SC_NOT_FOUND);

    String errorMessage =
        String.format("{\"error\":\"Not Found\",\"path\":\"%s\",\"status\":404}", path);

    out.print(errorMessage);
  }

  private void serveStaticFile(
      HttpServletRequest request,
      HttpServletResponse response,
      String resourcePath,
      String contentType)
      throws IOException {
    try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.print("{\"error\":\"File not found\",\"status\":404}");
        return;
      }

      response.setContentType(contentType);
      response.setCharacterEncoding("UTF-8");

      byte[] buffer = new byte[4096];
      int bytesRead;
      java.io.OutputStream outputStream = response.getOutputStream();

      while ((bytesRead = is.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }

      outputStream.flush();
    }
  }
}
