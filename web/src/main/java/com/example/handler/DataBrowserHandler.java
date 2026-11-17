package com.example.handler;

import com.example.datasource.DataBrowserService;
import com.example.datasource.DataSourceRegistry;
import com.example.datasource.DataSourceStrategy;
import com.example.extlib.ExtLibManager;
import com.example.util.JsonUtil;
import com.example.util.RequestHelper;
import com.example.view.ResponseHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataBrowserHandler {

  private static final Logger logger = LoggerFactory.getLogger(DataBrowserHandler.class);
  private static final DataBrowserHandler INSTANCE = new DataBrowserHandler();

  private final DataBrowserService service = new DataBrowserService();

  private DataBrowserHandler() {}

  public static DataBrowserHandler getInstance() {
    return INSTANCE;
  }

  /** GET /api/data-browser/db-types */
  public void handleDbTypes(HttpServletRequest req, HttpServletResponse res) throws IOException {
    List<Map<String, String>> dbTypes = new ArrayList<>();
    for (DataSourceStrategy strategy : DataSourceRegistry.getInstance().all()) {
      dbTypes.add(Map.of("dbType", strategy.getDbType(), "displayName", strategy.getDisplayName()));
    }
    ResponseHelper.sendJsonText(res, 200, JsonUtil.successResponse(Map.of("dbTypes", dbTypes)));
  }

  /** POST /api/data-browser/driver-status { "dbType": "postgresql" } */
  public void handleDriverStatus(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      String dbType = body.get("dbType").getAsString();

      if (!ExtLibManager.getInstance().isSupported(dbType)) {
        ResponseHelper.sendJsonText(
            res, 400, JsonUtil.errorResponse("Bad Request", "Unknown dbType: " + dbType, 400));
        return;
      }

      boolean downloaded = ExtLibManager.getInstance().isDownloaded(dbType);
      ResponseHelper.sendJsonText(
          res, 200, JsonUtil.successResponse(Map.of("dbType", dbType, "downloaded", downloaded)));

    } catch (Exception e) {
      ResponseHelper.sendJsonText(res, 500, JsonUtil.errorResponse("Error", e.getMessage(), 500));
    }
  }

  /** POST /api/data-browser/download { "dbType": "postgresql" } */
  public void handleDownload(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      String dbType = body.get("dbType").getAsString();

      if (!ExtLibManager.getInstance().isSupported(dbType)) {
        ResponseHelper.sendJsonText(
            res, 400, JsonUtil.errorResponse("Bad Request", "Unknown dbType: " + dbType, 400));
        return;
      }

      ExtLibManager.getInstance().downloadAndLoad(dbType);
      Map<String, Object> data = Map.of("status", "ready", "dbType", dbType);
      ResponseHelper.sendJsonText(res, 200, JsonUtil.successResponse(data));

    } catch (Exception e) {
      logger.error("Driver download failed", e);
      ResponseHelper.sendJsonText(
          res, 500, JsonUtil.errorResponse("Download Failed", e.getMessage(), 500));
    }
  }

  /** POST /api/data-browser/connect { dbType, url?, account?, user, password, ... } */
  public void handleConnect(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      if (!body.has("dbType") || !body.has("user") || !body.has("password")) {
        ResponseHelper.sendJsonText(
            res,
            400,
            JsonUtil.errorResponse(
                "Bad Request", "Missing required fields: dbType, user, password", 400));
        return;
      }

      String dbType = body.get("dbType").getAsString().toLowerCase();
      String user = body.get("user").getAsString();
      String password = body.get("password").getAsString();

      DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(dbType);
      if (strategy == null) {
        ResponseHelper.sendJsonText(
            res, 400, JsonUtil.errorResponse("Bad Request", "Unknown dbType: " + dbType, 400));
        return;
      }

      // Collect all extra fields (everything except dbType/user/password) into a flat map
      Map<String, String> props = new HashMap<>();
      body.entrySet()
          .forEach(
              e -> {
                if (!e.getKey().equals("dbType")
                    && !e.getKey().equals("user")
                    && !e.getKey().equals("password")
                    && e.getValue().isJsonPrimitive()) {
                  props.put(e.getKey(), e.getValue().getAsString());
                }
              });

      String sessionId = service.connect(strategy, dbType, user, password, props);

      Map<String, Object> data = Map.of("sessionId", sessionId, "status", "connected");
      ResponseHelper.sendJsonText(res, 200, JsonUtil.successResponse(data));

    } catch (Exception e) {
      logger.error("Connection failed", e);
      ResponseHelper.sendJsonText(
          res, 400, JsonUtil.errorResponse("Connection Failed", e.getMessage(), 400));
    }
  }

  /** POST /api/data-browser/databases { "sessionId": "..." } */
  public void handleDatabases(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      String sessionId = body.get("sessionId").getAsString();

      ResponseHelper.sendJsonText(res, 200, JsonUtil.successResponse(service.databases(sessionId)));

    } catch (DataBrowserService.SessionExpiredException e) {
      sessionExpired(res);
    } catch (Exception e) {
      logger.error("Failed to list databases", e);
      ResponseHelper.sendJsonText(
          res, 500, JsonUtil.errorResponse("Query Failed", e.getMessage(), 500));
    }
  }

  /** POST /api/data-browser/select-database { "sessionId": "...", "database": "..." } */
  public void handleSelectDatabase(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      String sessionId = body.get("sessionId").getAsString();
      String database = body.get("database").getAsString();

      service.selectDatabase(sessionId, database);

      ResponseHelper.sendJsonText(res, 200, JsonUtil.successResponse(Map.of("database", database)));

    } catch (DataBrowserService.SessionExpiredException e) {
      sessionExpired(res);
    } catch (DataBrowserService.UnknownDbTypeException e) {
      ResponseHelper.sendJsonText(
          res, 400, JsonUtil.errorResponse("Bad Request", "Unknown dbType for session", 400));
    } catch (Exception e) {
      logger.error("Failed to switch database", e);
      ResponseHelper.sendJsonText(
          res, 400, JsonUtil.errorResponse("Switch Failed", e.getMessage(), 400));
    }
  }

  /** POST /api/data-browser/tables { "sessionId": "..." } */
  public void handleTables(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      String sessionId = body.get("sessionId").getAsString();
      String dbType = body.has("dbType") ? body.get("dbType").getAsString() : "";
      DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(dbType);
      if (strategy == null) {
        logger.warn(
            "No strategy found for dbType='{}', using default system schema filter", dbType);
      }
      Set<String> systemSchemas =
          strategy != null ? strategy.getSystemSchemas() : Set.of("information_schema");

      List<Map<String, Object>> result = service.tables(sessionId, systemSchemas);

      ResponseHelper.sendJsonText(res, 200, JsonUtil.successResponse(Map.of("schemas", result)));

    } catch (DataBrowserService.SessionExpiredException e) {
      sessionExpired(res);
    } catch (Exception e) {
      logger.error("Failed to list tables", e);
      ResponseHelper.sendJsonText(
          res, 500, JsonUtil.errorResponse("Query Failed", e.getMessage(), 500));
    }
  }

  /** POST /api/data-browser/query { sessionId, sql, page (1-based) } */
  public void handleQuery(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      String sessionId = body.get("sessionId").getAsString();
      String sql = body.get("sql").getAsString();
      int page = body.has("page") ? body.get("page").getAsInt() : 1;

      ResponseHelper.sendJsonText(
          res, 200, JsonUtil.successResponse(service.query(sessionId, sql, page)));

    } catch (DataBrowserService.SessionExpiredException e) {
      sessionExpired(res);
    } catch (Exception e) {
      logger.error("Query failed", e);
      ResponseHelper.sendJsonText(
          res, 400, JsonUtil.errorResponse("Query Failed", e.getMessage(), 400));
    }
  }

  /** POST /api/data-browser/disconnect { "sessionId": "..." } */
  public void handleDisconnect(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      service.disconnect(body.get("sessionId").getAsString());
      ResponseHelper.sendJsonText(
          res, 200, JsonUtil.successResponse(Map.of("status", "disconnected")));
    } catch (DataBrowserService.SessionExpiredException e) {
      sessionExpired(res);
    } catch (Exception e) {
      ResponseHelper.sendJsonText(res, 500, JsonUtil.errorResponse("Error", e.getMessage(), 500));
    }
  }

  private void sessionExpired(HttpServletResponse res) throws IOException {
    ResponseHelper.sendJsonText(
        res, 401, JsonUtil.errorResponse("Session Expired", "Session not found or expired", 401));
  }
}
