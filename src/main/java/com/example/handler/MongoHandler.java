package com.example.handler;

import com.example.mongo.MongoExtLibManager;
import com.example.mongo.MongoReflectionClient;
import com.example.mongo.MongoSessionManager;
import com.example.util.JsonUtil;
import com.example.util.RequestHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non-JDBC browser mode for plain MongoDB, driven entirely through MongoReflectionClient since the
 * driver is dynamically loaded (no compile-time com.mongodb.* dependency).
 */
public class MongoHandler {

  private static final Logger logger = LoggerFactory.getLogger(MongoHandler.class);
  private static final MongoHandler INSTANCE = new MongoHandler();
  private static final int DEFAULT_FIND_LIMIT = 100;

  private MongoHandler() {}

  public static MongoHandler getInstance() {
    return INSTANCE;
  }

  private void writeJson(HttpServletResponse res, int status, String body) throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.setCharacterEncoding("UTF-8");
    res.getWriter().print(body);
    res.getWriter().flush();
  }

  /** POST /api/data-browser/mongo/connect { "connectionString": "mongodb://..." } */
  public void handleConnect(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      if (!body.has("connectionString")) {
        writeJson(
            res,
            400,
            JsonUtil.errorResponse("Bad Request", "Missing required field: connectionString", 400));
        return;
      }
      String connectionString = body.get("connectionString").getAsString();

      ClassLoader loader = MongoExtLibManager.getInstance().downloadAndLoad();
      MongoReflectionClient reflectionClient = new MongoReflectionClient(loader);
      Object client = reflectionClient.connect(connectionString);
      List<String> databases = reflectionClient.listDatabaseNames(client);

      String sessionId = MongoSessionManager.getInstance().createSession(client, reflectionClient);

      Map<String, Object> data =
          Map.of("sessionId", sessionId, "status", "connected", "databases", databases);
      writeJson(res, 200, JsonUtil.successResponse(data));

    } catch (Exception e) {
      logger.error("Mongo connection failed", e);
      writeJson(res, 400, JsonUtil.errorResponse("Connection Failed", e.getMessage(), 400));
    }
  }

  /** POST /api/data-browser/mongo/collections { "sessionId": "...", "database": "..." } */
  public void handleCollections(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      String sessionId = body.get("sessionId").getAsString();
      String database = body.get("database").getAsString();

      MongoSessionManager.Session session = MongoSessionManager.getInstance().getSession(sessionId);
      if (session == null) {
        writeJson(
            res,
            401,
            JsonUtil.errorResponse("Session Expired", "Session not found or expired", 401));
        return;
      }

      Object db = session.reflectionClient().getDatabase(session.client(), database);
      List<String> collections = session.reflectionClient().listCollectionNames(db);

      writeJson(res, 200, JsonUtil.successResponse(Map.of("collections", collections)));

    } catch (Exception e) {
      logger.error("Failed to list Mongo collections", e);
      writeJson(res, 500, JsonUtil.errorResponse("Query Failed", e.getMessage(), 500));
    }
  }

  /**
   * POST /api/data-browser/mongo/find { sessionId, database, collection, filter?, limit? } filter
   * is an optional JSON string (MongoDB query filter); omitted/blank matches all documents.
   */
  public void handleFind(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      String sessionId = body.get("sessionId").getAsString();
      String database = body.get("database").getAsString();
      String collectionName = body.get("collection").getAsString();
      String filter = body.has("filter") ? body.get("filter").getAsString() : null;
      int limit = body.has("limit") ? body.get("limit").getAsInt() : DEFAULT_FIND_LIMIT;

      MongoSessionManager.Session session = MongoSessionManager.getInstance().getSession(sessionId);
      if (session == null) {
        writeJson(
            res,
            401,
            JsonUtil.errorResponse("Session Expired", "Session not found or expired", 401));
        return;
      }

      Object db = session.reflectionClient().getDatabase(session.client(), database);
      Object collection = session.reflectionClient().getCollection(db, collectionName);
      List<String> documentJsonStrings = session.reflectionClient().find(collection, filter, limit);

      List<JsonElement> documents = new ArrayList<>();
      for (String json : documentJsonStrings) {
        documents.add(JsonParser.parseString(json));
      }

      writeJson(res, 200, JsonUtil.successResponse(Map.of("documents", documents)));

    } catch (Exception e) {
      logger.error("Mongo find failed", e);
      writeJson(res, 400, JsonUtil.errorResponse("Query Failed", e.getMessage(), 400));
    }
  }

  /** POST /api/data-browser/mongo/disconnect { "sessionId": "..." } */
  public void handleDisconnect(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      JsonObject body =
          JsonParser.parseString(RequestHelper.readRequestBody(req)).getAsJsonObject();
      String sessionId = body.get("sessionId").getAsString();

      MongoSessionManager.Session session = MongoSessionManager.getInstance().getSession(sessionId);
      if (session == null) {
        writeJson(
            res,
            401,
            JsonUtil.errorResponse("Session Expired", "Session not found or expired", 401));
        return;
      }
      MongoSessionManager.getInstance().removeSession(sessionId);
      writeJson(res, 200, JsonUtil.successResponse(Map.of("status", "disconnected")));

    } catch (Exception e) {
      writeJson(res, 500, JsonUtil.errorResponse("Error", e.getMessage(), 500));
    }
  }
}
