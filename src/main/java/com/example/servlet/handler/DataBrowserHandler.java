package com.example.servlet.handler;

import com.example.extlib.ExtLibManager;
import com.example.extlib.SessionManager;
import com.example.servlet.util.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.sql.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataBrowserHandler {

  private static final Logger logger = LoggerFactory.getLogger(DataBrowserHandler.class);
  private static final DataBrowserHandler INSTANCE = new DataBrowserHandler();

  private DataBrowserHandler() {}

  public static DataBrowserHandler getInstance() {
    return INSTANCE;
  }

  private String readBody(HttpServletRequest req) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader r = req.getReader()) {
      String line;
      while ((line = r.readLine()) != null) sb.append(line).append('\n');
    }
    return sb.toString();
  }

  private void writeJson(HttpServletResponse res, int status, String body) throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.setCharacterEncoding("UTF-8");
    res.getWriter().print(body);
    res.getWriter().flush();
  }

  /** POST /api/data-browser/download { "dbType": "postgresql" } */
  public void handleDownload(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
      String dbType = body.get("dbType").getAsString();

      if (!ExtLibManager.getInstance().isSupported(dbType)) {
        writeJson(
            res, 400, JsonUtil.errorResponse("Bad Request", "Unknown dbType: " + dbType, 400));
        return;
      }

      ExtLibManager.getInstance().downloadAndLoad(dbType);
      Map<String, Object> data = Map.of("status", "ready", "dbType", dbType);
      writeJson(res, 200, JsonUtil.successResponse(data));

    } catch (Exception e) {
      logger.error("Driver download failed", e);
      writeJson(res, 500, JsonUtil.errorResponse("Download Failed", e.getMessage(), 500));
    }
  }

  /**
   * POST /api/data-browser/connect { dbType, url?, account?, user, password, warehouse?, database?,
   * schema?, role? }
   */
  public void handleConnect(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
      if (!body.has("dbType") || !body.has("user") || !body.has("password")) {
        writeJson(
            res,
            400,
            JsonUtil.errorResponse(
                "Bad Request", "Missing required fields: dbType, user, password", 400));
        return;
      }
      String dbType = body.get("dbType").getAsString().toLowerCase();
      String user = body.get("user").getAsString();
      String password = body.get("password").getAsString();

      String url;
      Map<String, String> extraProps = new HashMap<>();

      if ("snowflake".equals(dbType)) {
        String account = body.get("account").getAsString();
        url = "jdbc:snowflake://" + account + ".snowflakecomputing.com/";
        if (body.has("warehouse")) extraProps.put("warehouse", body.get("warehouse").getAsString());
        if (body.has("database")) extraProps.put("db", body.get("database").getAsString());
        if (body.has("schema")) extraProps.put("schema", body.get("schema").getAsString());
        if (body.has("role")) extraProps.put("role", body.get("role").getAsString());
      } else {
        url = body.get("url").getAsString();
      }

      Connection conn =
          ExtLibManager.getInstance().connect(dbType, url, user, password, extraProps);
      String sessionId = SessionManager.getInstance().createSession(conn);

      Map<String, Object> data = Map.of("sessionId", sessionId, "status", "connected");
      writeJson(res, 200, JsonUtil.successResponse(data));

    } catch (Exception e) {
      logger.error("Connection failed", e);
      writeJson(res, 400, JsonUtil.errorResponse("Connection Failed", e.getMessage(), 400));
    }
  }

  /** POST /api/data-browser/tables { "sessionId": "..." } */
  public void handleTables(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
      String sessionId = body.get("sessionId").getAsString();

      Connection conn = SessionManager.getInstance().getConnection(sessionId);
      if (conn == null) {
        writeJson(
            res,
            401,
            JsonUtil.errorResponse("Session Expired", "Session not found or expired", 401));
        return;
      }

      DatabaseMetaData meta = conn.getMetaData();
      Map<String, List<String>> schemaMap = new LinkedHashMap<>();

      try (ResultSet schemas = meta.getSchemas()) {
        while (schemas.next()) {
          schemaMap.put(schemas.getString("TABLE_SCHEM"), new ArrayList<>());
        }
      }

      if (schemaMap.isEmpty()) schemaMap.put("default", new ArrayList<>());

      for (String schema : schemaMap.keySet()) {
        try (ResultSet tables = meta.getTables(null, schema, "%", new String[] {"TABLE", "VIEW"})) {
          while (tables.next()) {
            schemaMap.get(schema).add(tables.getString("TABLE_NAME"));
          }
        }
      }

      List<Map<String, Object>> result = new ArrayList<>();
      schemaMap.forEach(
          (schema, tables) -> {
            if (!tables.isEmpty()) {
              result.add(Map.of("schema", schema, "tables", tables));
            }
          });

      writeJson(res, 200, JsonUtil.successResponse(Map.of("schemas", result)));

    } catch (Exception e) {
      logger.error("Failed to list tables", e);
      writeJson(res, 500, JsonUtil.errorResponse("Query Failed", e.getMessage(), 500));
    }
  }

  /** POST /api/data-browser/query { sessionId, sql, page (1-based) } */
  public void handleQuery(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
      String sessionId = body.get("sessionId").getAsString();
      String sql = body.get("sql").getAsString();
      int page = body.has("page") ? body.get("page").getAsInt() : 1;
      if (page < 1) page = 1;
      int pageSize = 100;
      int offset = (page - 1) * pageSize;

      Connection conn = SessionManager.getInstance().getConnection(sessionId);
      if (conn == null) {
        writeJson(
            res,
            401,
            JsonUtil.errorResponse("Session Expired", "Session not found or expired", 401));
        return;
      }

      sql = sql.strip();
      if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).stripTrailing();
      String pagedSql = "SELECT * FROM (" + sql + ") __q LIMIT " + pageSize + " OFFSET " + offset;

      List<String> columns = new ArrayList<>();
      List<List<Object>> rows = new ArrayList<>();

      try (Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(pagedSql)) {

        ResultSetMetaData rsMeta = rs.getMetaData();
        int colCount = rsMeta.getColumnCount();
        for (int i = 1; i <= colCount; i++) columns.add(rsMeta.getColumnName(i));

        while (rs.next()) {
          List<Object> row = new ArrayList<>();
          for (int i = 1; i <= colCount; i++) row.add(toSafeValue(rs.getObject(i)));
          rows.add(row);
        }
      }

      Map<String, Object> data = new LinkedHashMap<>();
      data.put("columns", columns);
      data.put("rows", rows);
      data.put("page", page);
      data.put("pageSize", pageSize);
      data.put("hasMore", rows.size() == pageSize);

      writeJson(res, 200, JsonUtil.successResponse(data));

    } catch (Exception e) {
      logger.error("Query failed", e);
      writeJson(res, 400, JsonUtil.errorResponse("Query Failed", e.getMessage(), 400));
    }
  }

  /** POST /api/data-browser/disconnect { "sessionId": "..." } */
  public void handleDisconnect(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body = JsonParser.parseString(readBody(req)).getAsJsonObject();
      String sessionId = body.get("sessionId").getAsString();
      Connection conn = SessionManager.getInstance().getConnection(sessionId);
      if (conn == null) {
        writeJson(
            res,
            401,
            JsonUtil.errorResponse("Session Expired", "Session not found or expired", 401));
        return;
      }
      SessionManager.getInstance().removeSession(sessionId);
      writeJson(res, 200, JsonUtil.successResponse(Map.of("status", "disconnected")));
    } catch (Exception e) {
      writeJson(res, 500, JsonUtil.errorResponse("Error", e.getMessage(), 500));
    }
  }

  private Object toSafeValue(Object value) {
    if (value == null) return null;
    if (value instanceof Number || value instanceof Boolean) return value;
    if (value instanceof byte[])
      return java.util.Base64.getEncoder().encodeToString((byte[]) value);
    return value.toString();
  }
}
