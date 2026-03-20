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
