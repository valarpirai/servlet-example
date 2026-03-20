package com.example.servlet.route;

import com.example.servlet.handler.AttachmentHandler;
import com.example.servlet.handler.DataBrowserHandler;
import com.example.servlet.model.ProcessorResponse;
import com.example.servlet.model.Route;
import com.example.servlet.processor.ModuleProcessor;
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
        return handleHandler(route, pathParams, request, response);

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

      byte[] buffer = new byte[4096];
      int bytesRead;
      java.io.OutputStream outputStream = response.getOutputStream();

      while ((bytesRead = is.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }

      outputStream.flush();
      return true;
    }
  }

  /** Handle custom handler invocation via reflection. */
  private boolean handleHandler(
      Route route,
      Map<String, String> pathParams,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {

    String handlerName = route.getHandler();
    String methodName = route.getHandlerMethod();

    try {
      // Get handler instance (singleton pattern)
      Object handler = getHandlerInstance(handlerName);
      if (handler == null) {
        logger.error("Handler not found: {}", handlerName);
        return false;
      }

      // Set content type if specified
      if (route.getContentType() != null) {
        response.setContentType(route.getContentType());
        response.setCharacterEncoding("UTF-8");
      }

      // Invoke handler method
      if (pathParams.isEmpty()) {
        // No path parameters: handler(request, response) or handler(response)
        Method method = findMethod(handler.getClass(), methodName, HttpServletResponse.class);
        if (method == null) {
          method =
              findMethod(
                  handler.getClass(),
                  methodName,
                  HttpServletRequest.class,
                  HttpServletResponse.class);
        }

        if (method != null) {
          if (method.getParameterCount() == 1) {
            method.invoke(handler, response);
          } else {
            method.invoke(handler, request, response);
          }
          return true;
        }
      } else {
        // Has path parameters: handler(request, response, param1, param2, ...)
        String[] paramValues = pathParams.values().toArray(new String[0]);

        if (paramValues.length == 1) {
          // Common case: handler(request, response, id)
          Method method =
              findMethod(
                  handler.getClass(),
                  methodName,
                  HttpServletRequest.class,
                  HttpServletResponse.class,
                  String.class);

          if (method == null) {
            // Try: handler(response, id)
            method =
                findMethod(handler.getClass(), methodName, HttpServletResponse.class, String.class);
          }

          if (method != null) {
            if (method.getParameterCount() == 2) {
              method.invoke(handler, response, paramValues[0]);
            } else {
              method.invoke(handler, request, response, paramValues[0]);
            }
            return true;
          }
        }
      }

      logger.error("Handler method not found: {}.{}", handlerName, methodName);
      return false;

    } catch (Exception e) {
      logger.error("Error invoking handler: {}.{}", handlerName, methodName, e);
      throw new IOException("Handler invocation failed", e);
    }
  }

  /** Handle processor invocation (ModuleProcessor, etc.). */
  private boolean handleProcessor(
      Route route, HttpServletRequest request, HttpServletResponse response) throws IOException {

    String processorName = route.getProcessor();

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
      case "AttachmentHandler":
        return AttachmentHandler.getInstance();
      case "DataBrowserHandler":
        return DataBrowserHandler.getInstance();
      default:
        return null;
    }
  }

  /** Get processor instance by name (creates new instance). */
  private Object getProcessorInstance(String processorName) {
    switch (processorName) {
      case "ModuleProcessor":
        return new ModuleProcessor();
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
