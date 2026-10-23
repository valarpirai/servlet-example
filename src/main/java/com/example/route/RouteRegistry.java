package com.example.route;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.model.Route;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Registry for loading and matching routes. Routes live in the {@code routes} table. The table is
 * checked for changes every few seconds, so an edit goes live without a restart.
 *
 * <p>{@code routes.yml} is deprecated. It is read only when the database is not available or the
 * table has no active routes. A route in the table that fails validation is skipped with a warning.
 * A route in routes.yml that fails validation stops startup.
 */
public class RouteRegistry {

  private static final Logger logger = LoggerFactory.getLogger(RouteRegistry.class);

  /** How often the table is checked for changes. */
  static final long REFRESH_MILLIS = 5000;

  private static final String FINGERPRINT_SQL =
      "select md5(coalesce(string_agg(cast(r as text), ',' order by r.id), '')) from routes r";

  private static final String DEFAULT_SCRIPT_METHOD = "httpHandler";

  private static RouteRegistry instance;

  private final Database fixedDatabase;
  private final long refreshMillis;
  private volatile List<Route> routes = List.of();
  private volatile String fingerprint;
  private volatile long lastCheck;

  private RouteRegistry() {
    this(null);
  }

  /**
   * Creates a registry that reads routes from the given database.
   *
   * @param database database holding the routes table, or null to use the application database
   */
  RouteRegistry(Database database) {
    this(database, REFRESH_MILLIS);
  }

  /**
   * Creates a registry with its own check interval.
   *
   * @param database database holding the routes table, or null to use the application database
   * @param refreshMillis how long to wait between checks for changed routes
   */
  RouteRegistry(Database database, long refreshMillis) {
    this.fixedDatabase = database;
    this.refreshMillis = refreshMillis;
    load();
  }

  public static synchronized RouteRegistry getInstance() {
    if (instance == null) {
      instance = new RouteRegistry();
    }
    return instance;
  }

  private Database database() {
    return fixedDatabase != null ? fixedDatabase : DbPropertiesLoader.getDatabase();
  }

  /** Loads from the table, or from routes.yml when the table cannot supply routes. */
  private synchronized void load() {
    lastCheck = System.currentTimeMillis();
    Database db = database();
    if (db != null) {
      try {
        String seen = readFingerprint(db);
        List<Route> loaded = loadFromTable(db);
        fingerprint = seen;
        if (!loaded.isEmpty()) {
          routes = List.copyOf(loaded);
          logger.info("Loaded {} routes from the routes table", loaded.size());
          return;
        }
        logger.warn("The routes table has no usable routes. Using deprecated routes.yml");
      } catch (Exception e) {
        logger.warn(
            "Cannot read the routes table ({}). Using deprecated routes.yml", e.getMessage());
      }
    } else {
      logger.warn("No database. Using deprecated routes.yml");
    }
    loadFromYaml();
  }

  /** Reloads when the table changed since the last load. Keeps the current routes on an error. */
  private void refreshIfStale() {
    long now = System.currentTimeMillis();
    if (now - lastCheck < refreshMillis) {
      return;
    }
    synchronized (this) {
      if (now - lastCheck < refreshMillis) {
        return;
      }
      lastCheck = now;
      Database db = database();
      if (db == null) {
        return;
      }
      try {
        String seen = readFingerprint(db);
        if (seen.equals(fingerprint)) {
          return;
        }
        List<Route> loaded = loadFromTable(db);
        fingerprint = seen;
        if (!loaded.isEmpty()) {
          routes = List.copyOf(loaded);
          logger.info("Routes changed. Reloaded {} routes from the routes table", loaded.size());
        } else {
          logger.warn("The routes table has no usable routes. Keeping the current routes");
        }
      } catch (Exception e) {
        logger.warn("Cannot refresh routes ({}). Keeping the current routes", e.getMessage());
      }
    }
  }

  private String readFingerprint(Database db) throws Exception {
    return db.query(dsl -> String.valueOf(dsl.fetchValue(FINGERPRINT_SQL)));
  }

  /** Reads the active rows in match order. Rows that fail validation are skipped. */
  private List<Route> loadFromTable(Database db) throws Exception {
    List<Route> loaded = new ArrayList<>();
    List<Record> rows =
        db.query(
            dsl ->
                dsl.select()
                    .from(table("routes"))
                    .where(field("active", Boolean.class).isTrue())
                    .orderBy(field("sort_order"), field("id"))
                    .fetch());

    for (Record row : rows) {
      Long id = row.get("id", Long.class);
      try {
        Route route = toRoute(row);
        List<String> errors = new ArrayList<>();
        validateRoute(route, "Route id " + id + " (" + route.getPath() + ")", errors);
        if (errors.isEmpty()) {
          loaded.add(route);
        } else {
          logger.warn("Skipping route: {}", String.join("; ", errors));
        }
      } catch (RuntimeException e) {
        logger.warn("Skipping route id {}: {}", id, e.getMessage());
      }
    }
    return loaded;
  }

  private Route toRoute(Record row) {
    Route route = new Route();
    route.setId(row.get("id", Long.class));
    route.setPath(row.get("path", String.class));
    route.setMethods(
        split(row.get("methods", String.class)).stream().map(String::toUpperCase).toList());
    route.setType(row.get("type", String.class));
    route.setHandler(row.get("handler", String.class));
    route.setHandlerMethod(row.get("handler_method", String.class));
    route.setProcessor(row.get("processor", String.class));
    route.setResource(row.get("resource", String.class));
    route.setContentType(row.get("content_type", String.class));
    route.setDescription(row.get("description", String.class));
    route.setPathParams(split(row.get("path_params", String.class)));
    route.setDefaultRoute(Boolean.TRUE.equals(row.get("is_default", Boolean.class)));
    if ("script".equals(route.getType())
        && (route.getHandlerMethod() == null || route.getHandlerMethod().isBlank())) {
      route.setHandlerMethod(DEFAULT_SCRIPT_METHOD);
    }
    return route;
  }

  private static List<String> split(String commaSeparated) {
    if (commaSeparated == null || commaSeparated.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(commaSeparated.split(",")).map(String::trim).toList();
  }

  /** Deprecated. Loads routes.yml and stops startup when a route is invalid. */
  @SuppressWarnings("unchecked")
  private void loadFromYaml() {
    List<Route> loaded = new ArrayList<>();
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
        route.setDefaultRoute(true);
        loaded.add(route);
        logger.debug("Loaded route: {}", route);
      }

      logger.info("Loaded {} routes from routes.yml", loaded.size());

    } catch (Exception e) {
      logger.error("Failed to load routes.yml", e);
    }
    routes = List.copyOf(loaded);
    validateRoutes();
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
    refreshIfStale();
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
      validateRoute(route, routeDesc, errors);
    }

    if (!errors.isEmpty()) {
      StringBuilder errorMsg = new StringBuilder("Route validation failed:\n");
      for (String error : errors) {
        errorMsg.append("  - ").append(error).append("\n");
      }
      logger.error(errorMsg.toString());
      throw new IllegalStateException(
          errorMsg.toString() + "\nFix routes.yml (deprecated) and restart the application");
    }

    logger.info("Route validation passed: {} routes validated successfully", routes.size());
  }

  /** Adds one error per problem found in the route. */
  private void validateRoute(Route route, String routeDesc, List<String> errors) {
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
        case "script":
          validateScriptRoute(route, routeDesc, errors);
          break;
        default:
          errors.add(routeDesc + ": Unknown route type '" + route.getType() + "'");
      }
    }
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
        if (iface.getName().equals("com.example.processor.IRequestProcessor")) {
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

  private void validateScriptRoute(Route route, String routeDesc, List<String> errors) {
    if (route.getHandler() == null || route.getHandler().trim().isEmpty()) {
      errors.add(routeDesc + ": Script route missing 'handler' field (the api_scripts name)");
    }
    if (route.getHandlerMethod() == null || route.getHandlerMethod().trim().isEmpty()) {
      errors.add(routeDesc + ": Script route missing 'handlerMethod' field (the function name)");
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
      case "ApiHandler":
        return "com.example.handler.ApiHandler";
      case "AttachmentHandler":
        return "com.example.handler.AttachmentHandler";
      case "DataBrowserHandler":
        return "com.example.handler.DataBrowserHandler";
      case "MongoHandler":
        return "com.example.handler.MongoHandler";
      case "TableBrowserHandler":
        return "com.example.handler.TableBrowserHandler";
      default:
        return null;
    }
  }

  /** Map processor name from routes.yml to fully qualified class name. */
  private String getProcessorClassName(String processorName) {
    switch (processorName) {
      case "ModuleProcessor":
        return "com.example.processor.ModuleProcessor";
      case "FileUploadProcessor":
        return "com.example.processor.FileUploadProcessor";
      case "ScriptProcessor":
        return "com.example.processor.ScriptProcessor";
      case "TemplateProcessor":
        return "com.example.processor.TemplateProcessor";
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
