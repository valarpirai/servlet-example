package com.example.handler;

import com.example.browser.TableBrowser;
import com.example.browser.TableEditor;
import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.util.JsonUtil;
import com.example.util.RequestHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JSON endpoints behind the table browser pages: read, create, update and delete. */
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

  // POST /api/tables/{table}/row   body {"values": {column: value}}
  public void handleCreate(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String table = (String) request.getAttribute("pathParam.table");
    Database database = database(response);
    if (database == null) {
      return;
    }
    try {
      String key = new TableEditor(database).create(table, values(request));
      write(response, 201, Map.of("key", key));
    } catch (Exception e) {
      writeFailure(response, e, "Failed to create a row in " + table);
    }
  }

  // PUT /api/tables/{table}/row?id=   body {"values": {column: value}}
  public void handleUpdate(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String table = (String) request.getAttribute("pathParam.table");
    String id = request.getParameter("id");
    Database database = database(response);
    if (database == null) {
      return;
    }
    try {
      if (new TableEditor(database).update(table, id, values(request))) {
        write(response, 200, Map.of("updated", true));
      } else {
        sendError(response, 404, "Row not found: " + table + " id=" + id);
      }
    } catch (Exception e) {
      writeFailure(response, e, "Failed to update " + table + " id=" + id);
    }
  }

  // POST /api/tables/{table}/rows/delete   body {"ids": [key, ...]}
  public void handleDelete(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String table = (String) request.getAttribute("pathParam.table");
    Database database = database(response);
    if (database == null) {
      return;
    }
    try {
      Object ids = body(request).get("ids");
      List<String> keys = new ArrayList<>();
      if (ids instanceof List<?> list) {
        list.forEach(
            i ->
                keys.add(
                    i instanceof Double d && d == Math.rint(d)
                        ? String.valueOf(d.longValue())
                        : String.valueOf(i)));
      }
      if (keys.isEmpty()) {
        sendError(response, 400, "ids must list at least one key");
        return;
      }
      write(response, 200, new TableEditor(database).delete(table, keys));
    } catch (Exception e) {
      writeFailure(response, e, "Failed to delete from " + table);
    }
  }

  // ---- helpers ----

  /** Reads the JSON body as a map. A missing or malformed body is an empty map. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> body(HttpServletRequest request) throws IOException {
    String text = RequestHelper.readRequestBody(request);
    Map<String, Object> parsed =
        text == null || text.isBlank() ? null : JsonUtil.fromJson(text, Map.class);
    return parsed == null ? Map.of() : parsed;
  }

  /** The {@code values} object of a create or update body. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> values(HttpServletRequest request) throws IOException {
    Object values = body(request).get("values");
    if (!(values instanceof Map<?, ?>)) {
      throw new IllegalArgumentException("Body must hold a values object");
    }
    return (Map<String, Object>) values;
  }

  /** Maps a failed write to a status: 403 read-only, 409 rule stopped it, 400 bad input. */
  private void writeFailure(HttpServletResponse response, Exception e, String log)
      throws IOException {
    if (e instanceof TableEditor.NotWritableException) {
      sendError(response, 403, e.getMessage());
    } else if (e instanceof TableEditor.BlockedException) {
      sendError(response, 409, e.getMessage());
    } else if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
      sendError(response, 400, e.getMessage());
    } else {
      logger.error(log, e);
      sendError(response, 500, e.getMessage());
    }
  }

  private Database database(HttpServletResponse response) throws IOException {
    Database database = DbPropertiesLoader.getDatabase();
    if (database == null) {
      sendError(response, 503, "Database not configured");
    }
    return database;
  }

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
