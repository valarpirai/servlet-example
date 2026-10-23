package com.example.handler;

import com.example.browser.TableBrowser;
import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JSON endpoints behind the table browser pages. Read-only. */
public class TableBrowserHandler {

  private static final Logger logger = LoggerFactory.getLogger(TableBrowserHandler.class);
  private static TableBrowserHandler instance;

  private TableBrowserHandler() {}

  public static synchronized TableBrowserHandler getInstance() {
    if (instance == null) {
      instance = new TableBrowserHandler();
    }
    return instance;
  }

  // GET /api/tables
  public void handleTables(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    TableBrowser browser = browser(response);
    if (browser == null) {
      return;
    }
    try {
      write(response, 200, Map.of("tables", browser.listTables()));
    } catch (Exception e) {
      logger.error("Failed to list tables", e);
      sendError(response, 500, e.getMessage());
    }
  }

  // GET /api/tables/{table}/rows?page=&size=&sort=&dir=
  public void handleRows(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String table = (String) request.getAttribute("pathParam.table");
    TableBrowser browser = browser(response);
    if (browser == null) {
      return;
    }
    try {
      reply(
          response,
          browser.rows(
              table,
              intParam(request, "page", 1),
              intParam(request, "size", 0),
              request.getParameter("sort"),
              "desc".equalsIgnoreCase(request.getParameter("dir"))),
          "Table not found: " + table);
    } catch (IllegalArgumentException e) {
      sendError(response, 400, e.getMessage());
    } catch (Exception e) {
      logger.error("Failed to read table {}", table, e);
      sendError(response, 500, e.getMessage());
    }
  }

  // GET /api/tables/{table}/row?id=
  public void handleRow(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String table = (String) request.getAttribute("pathParam.table");
    String id = request.getParameter("id");
    TableBrowser browser = browser(response);
    if (browser == null) {
      return;
    }
    try {
      reply(response, browser.row(table, id), "Row not found: " + table + " id=" + id);
    } catch (Exception e) {
      logger.error("Failed to read row {} id={}", table, id, e);
      sendError(response, 500, e.getMessage());
    }
  }

  // ---- helpers ----

  /** Reads a whole-number query parameter. A missing or malformed value gives the fallback. */
  private int intParam(HttpServletRequest request, String name, int fallback) {
    try {
      return Integer.parseInt(request.getParameter(name));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private TableBrowser browser(HttpServletResponse response) throws IOException {
    Database database = DbPropertiesLoader.getDatabase();
    if (database == null) {
      sendError(response, 503, "Database not configured");
      return null;
    }
    return new TableBrowser(database);
  }

  private void reply(HttpServletResponse response, Optional<Map<String, Object>> body, String miss)
      throws IOException {
    if (body.isEmpty()) {
      sendError(response, 404, miss);
    } else {
      write(response, 200, body.get());
    }
  }

  private void write(HttpServletResponse response, int status, Object body) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    PrintWriter out = response.getWriter();
    out.print(JsonUtil.toJson(body));
    out.flush();
  }

  private void sendError(HttpServletResponse response, int status, String message)
      throws IOException {
    write(response, status, Map.of("error", message, "status", status));
  }
}
