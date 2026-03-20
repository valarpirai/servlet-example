package com.example.servlet.route;

import com.example.servlet.model.Route;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Registry for loading and matching routes from routes.yml configuration. Singleton pattern for
 * global access.
 */
public class RouteRegistry {

  private static final Logger logger = LoggerFactory.getLogger(RouteRegistry.class);
  private static RouteRegistry instance;
  private final List<Route> routes;

  private RouteRegistry() {
    this.routes = new ArrayList<>();
    loadRoutes();
    validateRoutes();
  }

  public static synchronized RouteRegistry getInstance() {
    if (instance == null) {
      instance = new RouteRegistry();
    }
    return instance;
  }

  /** Load routes from routes.yml in classpath. */
  @SuppressWarnings("unchecked")
  private void loadRoutes() {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream("routes.yml")) {

      if (input == null) {
        logger.error("routes.yml not found in classpath");
        return;
      }

      Yaml yaml = new Yaml();
      Map<String, Object> config = yaml.load(input);
      List<Map<String, Object>> routeConfigs = (List<Map<String, Object>>) config.get("routes");

      if (routeConfigs == null) {
        logger.warn("No routes defined in routes.yml");
        return;
      }

      for (Map<String, Object> routeConfig : routeConfigs) {
        Route route = parseRoute(routeConfig);
        routes.add(route);
        logger.debug("Loaded route: {}", route);
      }

      logger.info("Loaded {} routes from routes.yml", routes.size());

    } catch (Exception e) {
      logger.error("Failed to load routes.yml", e);
    }
  }

  /** Parse a single route configuration map into a Route object. */
  @SuppressWarnings("unchecked")
  private Route parseRoute(Map<String, Object> config) {
    Route route = new Route();

    route.setPath((String) config.get("path"));
    route.setType((String) config.get("type"));
    route.setHandler((String) config.get("handler"));
    route.setHandlerMethod((String) config.get("handlerMethod"));
    route.setProcessor((String) config.get("processor"));
    route.setResource((String) config.get("resource"));
    route.setContentType((String) config.get("contentType"));
    route.setDescription((String) config.get("description"));

    // Handle method (single string or list)
    Object methodObj = config.get("method");
    if (methodObj instanceof String) {
      route.setMethod((String) methodObj);
    } else if (methodObj instanceof List) {
      route.setMethods((List<String>) methodObj);
    }

    // Handle path parameters
    Object paramsObj = config.get("pathParams");
    if (paramsObj instanceof List) {
      route.setPathParams((List<String>) paramsObj);
    }

    return route;
  }

  /**
   * Find the first route that matches the given method and path.
   *
   * @param method HTTP method (GET, POST, etc.)
   * @param path Request path
   * @return Matching route or null if not found
   */
  public RouteMatch findRoute(String method, String path) {
    for (Route route : routes) {
      if (route.matches(method, path)) {
        Map<String, String> pathParams = route.extractPathParams(path);
        return new RouteMatch(route, pathParams);
      }
    }
    return null;
  }

  /** Get all loaded routes (for debugging/introspection). */
  public List<Route> getAllRoutes() {
    return new ArrayList<>(routes);
  }

  /**
   * Validate all loaded routes at startup. Fails fast with detailed error messages for
   * misconfigurations.
   */
  private void validateRoutes() {
    List<String> errors = new ArrayList<>();

    for (int i = 0; i < routes.size(); i++) {
      Route route = routes.get(i);
      String routeDesc =
          String.format("Route #%d (%s %s)", i + 1, route.getMethods(), route.getPath());

      // Validate required fields
      if (route.getPath() == null || route.getPath().trim().isEmpty()) {
        errors.add(routeDesc + ": Missing required field 'path'");
      }

      if (route.getMethods() == null || route.getMethods().isEmpty()) {
        errors.add(routeDesc + ": Missing required field 'method'");
      }

      if (route.getType() == null || route.getType().trim().isEmpty()) {
        errors.add(routeDesc + ": Missing required field 'type'");
      }

      // Validate type-specific requirements
      if (route.getType() != null) {
        switch (route.getType()) {
          case "static":
            validateStaticRoute(route, routeDesc, errors);
            break;
          case "handler":
            validateHandlerRoute(route, routeDesc, errors);
            break;
          case "processor":
            validateProcessorRoute(route, routeDesc, errors);
            break;
          case "builtin":
            validateBuiltinRoute(route, routeDesc, errors);
            break;
          default:
            errors.add(routeDesc + ": Unknown route type '" + route.getType() + "'");
        }
      }
    }

    if (!errors.isEmpty()) {
      StringBuilder errorMsg = new StringBuilder("Route validation failed:\n");
      for (String error : errors) {
        errorMsg.append("  - ").append(error).append("\n");
      }
      logger.error(errorMsg.toString());
      throw new IllegalStateException(
          errorMsg.toString() + "\nFix routes.yml and restart the application");
    }

    logger.info("Route validation passed: {} routes validated successfully", routes.size());
  }

  private void validateStaticRoute(Route route, String routeDesc, List<String> errors) {
    if (route.getResource() == null || route.getResource().trim().isEmpty()) {
      errors.add(routeDesc + ": Static route missing 'resource' field");
      return;
    }

    // Validate resource exists in classpath
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(route.getResource())) {
      if (is == null) {
        errors.add(routeDesc + ": Static resource not found in classpath: " + route.getResource());
      }
    } catch (Exception e) {
      errors.add(
          routeDesc
              + ": Error checking static resource '"
              + route.getResource()
              + "': "
              + e.getMessage());
    }

    if (route.getContentType() == null || route.getContentType().trim().isEmpty()) {
      errors.add(routeDesc + ": Static route missing 'contentType' field");
    }
  }

  private void validateHandlerRoute(Route route, String routeDesc, List<String> errors) {
    if (route.getHandler() == null || route.getHandler().trim().isEmpty()) {
      errors.add(routeDesc + ": Handler route missing 'handler' field");
      return;
    }

    if (route.getHandlerMethod() == null || route.getHandlerMethod().trim().isEmpty()) {
      errors.add(routeDesc + ": Handler route missing 'handlerMethod' field");
      return;
    }

    // Validate handler class exists
    String handlerClassName = getHandlerClassName(route.getHandler());
    if (handlerClassName == null) {
      errors.add(routeDesc + ": Unknown handler '" + route.getHandler() + "'");
      return;
    }

    try {
      Class<?> handlerClass = Class.forName(handlerClassName);

      // Check if handler has getInstance() method (singleton pattern)
      try {
        handlerClass.getMethod("getInstance");
      } catch (NoSuchMethodException e) {
        errors.add(
            routeDesc + ": Handler class '" + handlerClassName + "' missing getInstance() method");
      }

      // Validate handler method exists (try common signatures)
      boolean methodFound = false;
      String methodName = route.getHandlerMethod();

      // Try different method signatures
      Class<?>[][] possibleSignatures = {
        {jakarta.servlet.http.HttpServletResponse.class},
        {
          jakarta.servlet.http.HttpServletRequest.class,
          jakarta.servlet.http.HttpServletResponse.class
        },
        {jakarta.servlet.http.HttpServletResponse.class, String.class},
        {
          jakarta.servlet.http.HttpServletRequest.class,
          jakarta.servlet.http.HttpServletResponse.class,
          String.class
        }
      };

      for (Class<?>[] signature : possibleSignatures) {
        try {
          handlerClass.getMethod(methodName, signature);
          methodFound = true;
          break;
        } catch (NoSuchMethodException ignored) {
          // Try next signature
        }
      }

      if (!methodFound) {
        errors.add(
            routeDesc
                + ": Handler method '"
                + methodName
                + "' not found in class '"
                + handlerClassName
                + "'");
      }

    } catch (ClassNotFoundException e) {
      errors.add(routeDesc + ": Handler class not found: " + handlerClassName);
    }
  }

  private void validateProcessorRoute(Route route, String routeDesc, List<String> errors) {
    if (route.getProcessor() == null || route.getProcessor().trim().isEmpty()) {
      errors.add(routeDesc + ": Processor route missing 'processor' field");
      return;
    }

    // Validate processor class exists
    String processorClassName = getProcessorClassName(route.getProcessor());
    if (processorClassName == null) {
      errors.add(routeDesc + ": Unknown processor '" + route.getProcessor() + "'");
      return;
    }

    try {
      Class<?> processorClass = Class.forName(processorClassName);

      // Check if processor has a public no-arg constructor
      try {
        processorClass.getConstructor();
      } catch (NoSuchMethodException e) {
        errors.add(
            routeDesc
                + ": Processor class '"
                + processorClassName
                + "' missing public no-arg constructor");
      }

      // Check if processor implements IRequestProcessor interface
      boolean implementsInterface = false;
      for (Class<?> iface : processorClass.getInterfaces()) {
        if (iface.getName().equals("com.example.servlet.processor.IRequestProcessor")) {
          implementsInterface = true;
          break;
        }
      }

      if (!implementsInterface) {
        errors.add(
            routeDesc
                + ": Processor class '"
                + processorClassName
                + "' does not implement IRequestProcessor interface");
      }

    } catch (ClassNotFoundException e) {
      errors.add(routeDesc + ": Processor class not found: " + processorClassName);
    }
  }

  private void validateBuiltinRoute(Route route, String routeDesc, List<String> errors) {
    if (route.getHandler() == null || route.getHandler().trim().isEmpty()) {
      errors.add(routeDesc + ": Builtin route missing 'handler' field");
    }

    // Validate handler method name matches expected builtin methods
    String handlerMethod = route.getHandler();
    if (handlerMethod != null) {
      switch (handlerMethod) {
        case "handleHealth":
        case "handleMetrics":
          // Valid builtin handlers
          break;
        default:
          errors.add(
              routeDesc
                  + ": Unknown builtin handler '"
                  + handlerMethod
                  + "' (expected: handleHealth, handleMetrics)");
      }
    }
  }

  /** Map handler name from routes.yml to fully qualified class name. */
  private String getHandlerClassName(String handlerName) {
    switch (handlerName) {
      case "AttachmentHandler":
        return "com.example.servlet.handler.AttachmentHandler";
      case "DataBrowserHandler":
        return "com.example.servlet.handler.DataBrowserHandler";
      default:
        return null;
    }
  }

  /** Map processor name from routes.yml to fully qualified class name. */
  private String getProcessorClassName(String processorName) {
    switch (processorName) {
      case "ModuleProcessor":
        return "com.example.servlet.processor.ModuleProcessor";
      case "FileUploadProcessor":
        return "com.example.servlet.processor.FileUploadProcessor";
      case "ScriptProcessor":
        return "com.example.servlet.processor.ScriptProcessor";
      case "TemplateProcessor":
        return "com.example.servlet.processor.TemplateProcessor";
      default:
        return null;
    }
  }

  /** Result of route matching, containing the route and extracted path parameters. */
  public static class RouteMatch {
    private final Route route;
    private final Map<String, String> pathParams;

    public RouteMatch(Route route, Map<String, String> pathParams) {
      this.route = route;
      this.pathParams = pathParams;
    }

    public Route getRoute() {
      return route;
    }

    public Map<String, String> getPathParams() {
      return pathParams;
    }

    public String getPathParam(String name) {
      return pathParams.get(name);
    }
  }
}
