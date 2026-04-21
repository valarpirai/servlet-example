package com.example.servlet.handler;

import com.example.config.AppProperty;
import com.example.config.DbPropertiesLoader;
import com.example.config.PropertyRepository;
import com.example.servlet.util.JsonUtil;
import com.example.servlet.util.PropertiesUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** REST handler for managing app_properties rows. */
public class PropertiesHandler {

  private static final Logger logger = LoggerFactory.getLogger(PropertiesHandler.class);
  private static PropertiesHandler instance;

  private PropertiesHandler() {}

  public static synchronized PropertiesHandler getInstance() {
    if (instance == null) {
      instance = new PropertiesHandler();
    }
    return instance;
  }

  // GET /api/properties
  public void handleList(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    PropertyRepository repo = DbPropertiesLoader.getRepository();
    if (repo == null) {
      sendError(response, 503, "Database not configured");
      return;
    }
    try {
      List<AppProperty> all = repo.findAll();
      write(response, 200, all.stream().map(this::toMap).toList());
    } catch (Exception e) {
      logger.error("Failed to list properties", e);
      sendError(response, 500, e.getMessage());
    }
  }

  // GET /api/properties/{id}
  public void handleGet(HttpServletRequest request, HttpServletResponse response, String id)
      throws IOException {
    PropertyRepository repo = DbPropertiesLoader.getRepository();
    if (repo == null) {
      sendError(response, 503, "Database not configured");
      return;
    }
    try {
      Optional<AppProperty> found = repo.findById(parseLong(id));
      if (found.isEmpty()) {
        sendError(response, 404, "Property not found: " + id);
        return;
      }
      write(response, 200, toMap(found.get()));
    } catch (Exception e) {
      logger.error("Failed to get property {}", id, e);
      sendError(response, 500, e.getMessage());
    }
  }

  // POST /api/properties
  public void handleCreate(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    PropertyRepository repo = DbPropertiesLoader.getRepository();
    if (repo == null) {
      sendError(response, 503, "Database not configured");
      return;
    }
    try {
      Map<?, ?> body =
          JsonUtil.fromJson(request.getReader().lines().reduce("", String::concat), Map.class);
      String name = (String) body.get("name");
      String value = (String) body.get("value");
      String type = (String) body.get("type");
      String description = (String) body.get("description");
      if (name == null || name.isBlank()) {
        sendError(response, 400, "Field 'name' is required");
        return;
      }
      AppProperty created = repo.create(name, value, type, description, "api");
      write(response, 201, toMap(created));
    } catch (Exception e) {
      logger.error("Failed to create property", e);
      sendError(response, 500, e.getMessage());
    }
  }

  // PUT /api/properties/{id}
  public void handleUpdate(HttpServletRequest request, HttpServletResponse response, String id)
      throws IOException {
    PropertyRepository repo = DbPropertiesLoader.getRepository();
    if (repo == null) {
      sendError(response, 503, "Database not configured");
      return;
    }
    try {
      Map<?, ?> body =
          JsonUtil.fromJson(request.getReader().lines().reduce("", String::concat), Map.class);
      String value = (String) body.get("value");
      String description = (String) body.get("description");
      Optional<AppProperty> updated = repo.update(parseLong(id), value, description, "api");
      if (updated.isEmpty()) {
        sendError(response, 404, "Property not found: " + id);
        return;
      }
      PropertiesUtil.reload(); // clear LRU cache so next read picks up new value
      write(response, 200, toMap(updated.get()));
    } catch (Exception e) {
      logger.error("Failed to update property {}", id, e);
      sendError(response, 500, e.getMessage());
    }
  }

  // PATCH /api/properties/{id}/active
  public void handleToggleActive(
      HttpServletRequest request, HttpServletResponse response, String id) throws IOException {
    PropertyRepository repo = DbPropertiesLoader.getRepository();
    if (repo == null) {
      sendError(response, 503, "Database not configured");
      return;
    }
    try {
      Map<?, ?> body =
          JsonUtil.fromJson(request.getReader().lines().reduce("", String::concat), Map.class);
      Object activeVal = body.get("active");
      if (activeVal == null) {
        sendError(response, 400, "Field 'active' is required");
        return;
      }
      boolean active = Boolean.parseBoolean(String.valueOf(activeVal));
      boolean updated = repo.setActive(parseLong(id), active, "api");
      if (!updated) {
        sendError(response, 404, "Property not found: " + id);
        return;
      }
      PropertiesUtil.reload();
      write(response, 200, Map.of("id", id, "active", active));
    } catch (Exception e) {
      logger.error("Failed to toggle active for property {}", id, e);
      sendError(response, 500, e.getMessage());
    }
  }

  // DELETE /api/properties/{id}
  public void handleDelete(HttpServletRequest request, HttpServletResponse response, String id)
      throws IOException {
    PropertyRepository repo = DbPropertiesLoader.getRepository();
    if (repo == null) {
      sendError(response, 503, "Database not configured");
      return;
    }
    try {
      boolean deleted = repo.delete(parseLong(id));
      if (!deleted) {
        sendError(response, 404, "Property not found: " + id);
        return;
      }
      PropertiesUtil.reload();
      write(response, 200, Map.of("deleted", true, "id", id));
    } catch (Exception e) {
      logger.error("Failed to delete property {}", id, e);
      sendError(response, 500, e.getMessage());
    }
  }

  // POST /api/properties/reload
  public void handleReload(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    PropertiesUtil.reload();
    write(response, 200, Map.of("reloaded", true));
  }

  // ---- helpers ----

  private Map<String, Object> toMap(AppProperty p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.getId());
    m.put("name", p.getName());
    m.put("value", p.getValue());
    m.put("type", p.getType());
    m.put("description", p.getDescription());
    m.put("active", p.isActive());
    m.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
    m.put("updatedAt", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
    m.put("createdBy", p.getCreatedBy());
    m.put("updatedBy", p.getUpdatedBy());
    return m;
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

  private long parseLong(String s) {
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid id: " + s);
    }
  }
}
