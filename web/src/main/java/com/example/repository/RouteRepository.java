package com.example.repository;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.model.Route;
import com.example.record.TableRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads routes from the {@code routes} table. */
public class RouteRepository {

  private static final Logger logger = LoggerFactory.getLogger(RouteRepository.class);
  private static final String ROUTES = "routes";
  private static final String DEFAULT_SCRIPT_METHOD = "httpHandler";

  private final Database fixedDatabase;

  /**
   * Creates a repository.
   *
   * @param database database holding the routes table, or null to use the application database
   */
  public RouteRepository(Database database) {
    this.fixedDatabase = database;
  }

  /**
   * Tells whether a database is available to read from.
   *
   * @return false when no database is configured
   */
  public boolean isAvailable() {
    return database() != null;
  }

  /**
   * Hashes every column of every row, so a change to any route changes the fingerprint.
   *
   * @return the hex MD5 of the whole table
   * @throws Exception when the query fails
   */
  public String fingerprint() throws Exception {
    TableRecord all = new TableRecord(database(), ROUTES);
    all.orderBy("id");
    all.query();
    StringBuilder text = new StringBuilder();
    while (all.next()) {
      for (String column : all.getColumnNames()) {
        text.append(column).append('=').append(all.getValue(column)).append('\u001f');
      }
      text.append('\u001e');
    }
    MessageDigest md5 = MessageDigest.getInstance("MD5");
    return HexFormat.of().formatHex(md5.digest(text.toString().getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Reads the active rows in match order. A row that fails to map is logged and skipped.
   *
   * @return the routes, ordered by sort_order and then id
   * @throws Exception when the query fails
   */
  public List<Route> activeRoutes() throws Exception {
    List<Route> routes = new ArrayList<>();
    TableRecord rows = new TableRecord(database(), ROUTES);
    rows.addQuery("active", true);
    rows.orderBy("sort_order");
    rows.orderBy("id");
    rows.query();
    while (rows.next()) {
      try {
        routes.add(toRoute(rows));
      } catch (RuntimeException e) {
        logger.warn("Skipping route id {}: {}", rows.getValue("id"), e.getMessage());
      }
    }
    return routes;
  }

  private Database database() {
    return fixedDatabase != null ? fixedDatabase : DbPropertiesLoader.getDatabase();
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
    return Arrays.stream(commaSeparated.split(",")).map(String::trim).toList();
  }
}
