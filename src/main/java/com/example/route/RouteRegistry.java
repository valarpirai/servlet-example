package com.example.route;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.model.Route;
import com.example.record.TableRecord;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for loading and matching routes. Routes live in the {@code routes} table. The table is
 * checked for changes every few seconds, so an edit goes live without a restart.
 *
 * <p>The registry needs the database. Startup fails when the table cannot be read or has no usable
 * routes. A route in the table that fails validation is skipped with a warning.
 */
public class RouteRegistry {

  private static final Logger logger = LoggerFactory.getLogger(RouteRegistry.class);

  /** How often the table is checked for changes. */
  static final long REFRESH_MILLIS = 5000;

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

  /** Loads the routes at startup. Throws when the database or the routes table is unusable. */
  private synchronized void load() {
    lastCheck = System.currentTimeMillis();
    Database db = database();
    if (db == null) {
      throw new IllegalStateException("No database. The routes table is required");
    }
    try {
      String seen = readFingerprint(db);
      List<Route> loaded = loadFromTable(db);
      if (loaded.isEmpty()) {
        throw new IllegalStateException("The routes table has no usable routes");
      }
      fingerprint = seen;
      routes = List.copyOf(loaded);
      logger.info("Loaded {} routes from the routes table", loaded.size());
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Cannot read the routes table: " + e.getMessage(), e);
    }
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

  /** Hashes every column of every row, so a change to any route changes the fingerprint. */
  private String readFingerprint(Database db) throws Exception {
    TableRecord all = new TableRecord(db, "routes");
    all.orderBy("id");
    all.query();
    StringBuilder text = new StringBuilder();
    while (all.next()) {
      for (String column : all.getColumnNames()) {
        text.append(column).append('=').append(all.getValue(column)).append('\u001f');
      }
      text.append('\u001e');
    }
    java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
    return java.util.HexFormat.of()
        .formatHex(md5.digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  /** Reads the active rows in match order. Rows that fail validation are skipped. */
  private List<Route> loadFromTable(Database db) throws Exception {
    List<Route> loaded = new ArrayList<>();
    TableRecord rows = new TableRecord(db, "routes");
    rows.addQuery("active", true);
    rows.orderBy("sort_order");
    rows.orderBy("id");
    rows.query();

    while (rows.next()) {
      String id = rows.getValue("id");
      try {
        Route route = toRoute(rows);
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

  private Route toRoute(TableRecord row) {
    Route route = new Route();
    route.setId(Long.valueOf(row.getValue("id")));
    route.setPath(row.getValue("path"));
    route.setMethods(split(row.getValue("methods")).stream().map(String::toUpperCase).toList());
    route.setType(row.getValue("type"));
    route.setHandler(row.getValue("handler"));
    route.setHandlerMethod(row.getValue("handler_method"));
    route.setProcessor(row.getValue("processor"));
    route.setResource(row.getValue("resource"));
    route.setContentType(row.getValue("content_type"));
    route.setDescription(row.getValue("description"));
    route.setPathParams(split(row.getValue("path_params")));
    route.setDefaultRoute(Boolean.parseBoolean(row.getValue("is_default")));
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

  /** Map handler name from the routes table to fully qualified class name. */
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
      case "TableApiHandler":
        return "com.example.handler.TableApiHandler";
      case "SchedulerHandler":
        return "com.example.handler.SchedulerHandler";
      default:
        return null;
    }
  }

  /** Map processor name from the routes table to fully qualified class name. */
  private String getProcessorClassName(String processorName) {
    switch (processorName) {
      case "FileUploadProcessor":
        return "com.example.processor.FileUploadProcessor";
      case "ScriptProcessor":
        return "com.example.processor.ScriptProcessor";
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
