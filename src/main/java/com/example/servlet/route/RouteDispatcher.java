package com.example.servlet.route;

import com.example.servlet.handler.ApiHandler;
import com.example.servlet.handler.AttachmentHandler;
import com.example.servlet.handler.DataBrowserHandler;
import com.example.servlet.handler.MongoHandler;
import com.example.servlet.handler.PropertiesHandler;
import com.example.servlet.model.ProcessorResponse;
import com.example.servlet.model.Route;
import com.example.servlet.processor.FileUploadProcessor;
import com.example.servlet.processor.ModuleProcessor;
import com.example.servlet.processor.ScriptProcessor;
import com.example.servlet.processor.TemplateProcessor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches HTTP requests to appropriate handlers based on route configuration. Acts as a bridge
 * between RouteRegistry and actual handler implementations.
 */
public class RouteDispatcher {

  private static final Logger logger = LoggerFactory.getLogger(RouteDispatcher.class);

  // Whitelist of allowed static resource directories
  private static final String[] ALLOWED_RESOURCE_PREFIXES = {"static/", "templates/"};

  // Buffer size for streaming static files
  private static final int STATIC_FILE_BUFFER_SIZE = 4096;

  /**
   * Dispatch request to the matched route handler.
   *
   * @param match Route match with extracted parameters
   * @param request HTTP request
   * @param response HTTP response
   * @return true if handled, false if not found or error
   */
  public boolean dispatch(
      RouteRegistry.RouteMatch match, HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    Route route = match.getRoute();
    Map<String, String> pathParams = match.getPathParams();

    logger.debug(
        "Dispatching {} {} to handler type: {}",
        request.getMethod(),
        request.getPathInfo(),
        route.getType());

    switch (route.getType()) {
      case "static":
        return handleStatic(route, request, response);

      case "handler":
        setPathParamsAsAttributes(request, pathParams);
        return handleHandler(route, request, response);

      case "processor":
        return handleProcessor(route, request, response);

      case "builtin":
        // Builtin handlers need to be handled in RouterServlet directly
        // as they use instance methods like handleHealth()
        return false;

      default:
        logger.warn("Unknown route type: {}", route.getType());
        return false;
    }
  }

  /** Handle static file serving. */
  private boolean handleStatic(
      Route route, HttpServletRequest request, HttpServletResponse response) throws IOException {
    String resource = route.getResource();
    String contentType = route.getContentType();

    // Validate resource path for security
    if (!isValidResourcePath(resource)) {
      logger.warn("Rejected invalid resource path: {}", resource);
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      PrintWriter out = response.getWriter();
      out.print("{\"error\":\"Forbidden\",\"message\":\"Invalid resource path\",\"status\":403}");
      return true;
    }

    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
      if (is == null) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.print("{\"error\":\"File not found\",\"status\":404}");
        return true;
      }

      response.setContentType(contentType);
      response.setCharacterEncoding("UTF-8");

      byte[] buffer = new byte[STATIC_FILE_BUFFER_SIZE];
      int bytesRead;
      java.io.OutputStream outputStream = response.getOutputStream();

      while ((bytesRead = is.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }

      outputStream.flush();
      return true;
    }
  }

  /**
   * Validates a resource path to prevent path traversal attacks.
   *
   * @param resourcePath The resource path to validate
   * @return true if the path is safe to use
   */
  private boolean isValidResourcePath(String resourcePath) {
    if (resourcePath == null || resourcePath.isEmpty()) {
      return false;
    }

    // Reject absolute paths
    if (resourcePath.startsWith("/")) {
      return false;
    }

    // Reject path traversal attempts
    if (resourcePath.contains("..")) {
      return false;
    }

    // Reject null bytes (used in some attacks)
    if (resourcePath.contains("\0")) {
      return false;
    }

    // Normalize path separators (Windows compatibility)
    String normalizedPath = resourcePath.replace('\\', '/');

    // Check if path starts with an allowed prefix
    for (String allowedPrefix : ALLOWED_RESOURCE_PREFIXES) {
      if (normalizedPath.startsWith(allowedPrefix)) {
        return true;
      }
    }

    return false;
  }

  /** Expose path parameters as request attributes so handler methods can read them uniformly. */
  private void setPathParamsAsAttributes(
      HttpServletRequest request, Map<String, String> pathParams) {
    pathParams.forEach((name, value) -> request.setAttribute("pathParam." + name, value));
  }

  /**
   * Handle custom handler invocation. All handler methods must have the signature
   * (HttpServletRequest, HttpServletResponse) — path params are available via
   * request.getAttribute("pathParam.{name}").
   */
  private boolean handleHandler(
      Route route, HttpServletRequest request, HttpServletResponse response) throws IOException {

    String handlerName = route.getHandler();
    String methodName = route.getHandlerMethod();

    try {
      Object handler = getHandlerInstance(handlerName);
      if (handler == null) {
        logger.error("Handler not found: {}", handlerName);
        return false;
      }

      if (route.getContentType() != null) {
        response.setContentType(route.getContentType());
        response.setCharacterEncoding("UTF-8");
      }

      Method method =
          findMethod(
              handler.getClass(), methodName, HttpServletRequest.class, HttpServletResponse.class);

      if (method == null) {
        logger.error(
            "Handler method not found: {}.{}(HttpServletRequest, HttpServletResponse)",
            handlerName,
            methodName);
        return false;
      }

      method.invoke(handler, request, response);
      return true;

    } catch (Exception e) {
      logger.error("Error invoking handler: {}.{}", handlerName, methodName, e);
      throw new IOException("Handler invocation failed", e);
    }
  }

  /** Handle processor invocation (ModuleProcessor, etc.). */
  private boolean handleProcessor(
      Route route, HttpServletRequest request, HttpServletResponse response) throws IOException {

    String processorName = route.getProcessor();

    // If no processor specified, return false to allow fallback
    if (processorName == null || processorName.isEmpty()) {
      return false;
    }

    try {
      Object processor = getProcessorInstance(processorName);
      if (processor == null) {
        logger.error("Processor not found: {}", processorName);
        return false;
      }

      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");

      // Assume all processors implement process(HttpServletRequest)
      Method method = processor.getClass().getMethod("process", HttpServletRequest.class);
      ProcessorResponse processorResponse = (ProcessorResponse) method.invoke(processor, request);

      response.setStatus(processorResponse.getStatusCode());
      response.setContentType(processorResponse.getContentType());

      for (Map.Entry<String, String> header : processorResponse.getHeaders().entrySet()) {
        response.setHeader(header.getKey(), header.getValue());
      }

      PrintWriter out = response.getWriter();
      out.print(processorResponse.getBody());
      out.flush();

      return true;

    } catch (Exception e) {
      logger.error("Error invoking processor: {}", processorName, e);
      throw new IOException("Processor invocation failed", e);
    }
  }

  /** Get handler instance by name (singleton pattern). */
  private Object getHandlerInstance(String handlerName) {
    switch (handlerName) {
      case "ApiHandler":
        return ApiHandler.getInstance();
      case "AttachmentHandler":
        return AttachmentHandler.getInstance();
      case "DataBrowserHandler":
        return DataBrowserHandler.getInstance();
      case "MongoHandler":
        return MongoHandler.getInstance();
      case "PropertiesHandler":
        return PropertiesHandler.getInstance();
      default:
        return null;
    }
  }

  /** Get processor instance by name (creates new instance). */
  private Object getProcessorInstance(String processorName) {
    switch (processorName) {
      case "ModuleProcessor":
        return new ModuleProcessor();
      case "FileUploadProcessor":
        return new FileUploadProcessor();
      case "ScriptProcessor":
        return new ScriptProcessor();
      case "TemplateProcessor":
        return new TemplateProcessor();
      default:
        return null;
    }
  }

  /** Find method by name and parameter types. */
  private Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
    try {
      return clazz.getMethod(methodName, parameterTypes);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }
}
