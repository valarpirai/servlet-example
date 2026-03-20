package com.example.servlet.processor;

import com.example.servlet.model.Module;
import com.example.servlet.module.ModuleManager;
import com.example.servlet.util.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModuleProcessor implements RequestProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ModuleProcessor.class);
  private static final String CONTENT_TYPE = "application/json";
  private final ModuleManager moduleManager;

  public ModuleProcessor() {
    this.moduleManager = ModuleManager.getInstance();
  }

  @Override
  public boolean supports(String contentType) {
    return contentType != null && contentType.toLowerCase().startsWith(CONTENT_TYPE);
  }

  @Override
  public ProcessorResponse process(HttpServletRequest request)
      throws IOException, ServletException {
    String method = request.getMethod();
    String pathInfo = request.getPathInfo();

    // Extract module operation from path: /api/modules/{operation}
    String operation = extractOperation(pathInfo);

    return switch (method) {
      case "GET" -> handleGet(operation, request);
      case "POST" -> handlePost(operation, request);
      case "PUT" -> handlePut(operation, request);
      case "DELETE" -> handleDelete(operation, request);
      default ->
          ProcessorResponse.builder()
              .statusCode(405)
              .body(JsonUtil.errorResponse("Method Not Allowed", "Method not supported", 405))
              .build();
    };
  }

  @Override
  public String getContentType() {
    return CONTENT_TYPE;
  }

  private String extractOperation(String pathInfo) {
    if (pathInfo == null || pathInfo.equals("/api/modules")) {
      return "list";
    }
    return pathInfo.substring("/api/modules/".length());
  }

  private ProcessorResponse handleGet(String operation, HttpServletRequest request)
      throws IOException {
    if ("list".equals(operation)) {
      return handleListModules();
    } else {
      return handleGetModule(operation);
    }
  }

  private ProcessorResponse handleListModules() throws IOException {
    try {
      List<Module> modules = moduleManager.listModules();
      Map<String, Object> responseData = new HashMap<>();
      responseData.put("modules", modules);
      responseData.put("count", modules.size());

      return ProcessorResponse.builder()
          .statusCode(200)
          .body(JsonUtil.successResponse(responseData))
          .build();
    } catch (Exception e) {
      logger.error("Failed to list modules", e);
      return ProcessorResponse.builder()
          .statusCode(500)
          .body(
              JsonUtil.errorResponse(
                  "Internal Server Error", "Failed to list modules: " + e.getMessage(), 500))
          .build();
    }
  }

  private ProcessorResponse handleGetModule(String modulePath) throws IOException {
    try {
      Module module = moduleManager.getModule(modulePath);
      if (module == null) {
        return ProcessorResponse.builder()
            .statusCode(404)
            .body(JsonUtil.errorResponse("Not Found", "Module not found: " + modulePath, 404))
            .build();
      }

      return ProcessorResponse.builder()
          .statusCode(200)
          .body(JsonUtil.successResponse(module))
          .build();
    } catch (Exception e) {
      logger.error("Failed to get module: {}", modulePath, e);
      return ProcessorResponse.builder()
          .statusCode(500)
          .body(
              JsonUtil.errorResponse(
                  "Internal Server Error", "Failed to get module: " + e.getMessage(), 500))
          .build();
    }
  }

  private ProcessorResponse handlePost(String operation, HttpServletRequest request)
      throws IOException {
    String requestBody = readRequestBody(request);

    if (requestBody == null || requestBody.trim().isEmpty()) {
      return ProcessorResponse.builder()
          .statusCode(400)
          .body(JsonUtil.errorResponse("Bad Request", "Empty request body", 400))
          .build();
    }

    try {
      JsonObject json = JsonParser.parseString(requestBody).getAsJsonObject();

      if (!json.has("path") || !json.has("content")) {
        return ProcessorResponse.builder()
            .statusCode(400)
            .body(
                JsonUtil.errorResponse(
                    "Bad Request", "Missing required fields: path, content", 400))
            .build();
      }

      String modulePath = json.get("path").getAsString();
      String content = json.get("content").getAsString();

      Module module = moduleManager.createModule(modulePath, content);

      return ProcessorResponse.builder()
          .statusCode(201)
          .body(JsonUtil.successResponse(module))
          .build();
    } catch (IOException e) {
      logger.error("Failed to create module", e);
      return ProcessorResponse.builder()
          .statusCode(400)
          .body(JsonUtil.errorResponse("Bad Request", e.getMessage(), 400))
          .build();
    } catch (Exception e) {
      logger.error("Unexpected error creating module", e);
      return ProcessorResponse.builder()
          .statusCode(500)
          .body(
              JsonUtil.errorResponse(
                  "Internal Server Error", "Failed to create module: " + e.getMessage(), 500))
          .build();
    }
  }

  private ProcessorResponse handlePut(String operation, HttpServletRequest request)
      throws IOException {
    String requestBody = readRequestBody(request);

    if (requestBody == null || requestBody.trim().isEmpty()) {
      return ProcessorResponse.builder()
          .statusCode(400)
          .body(JsonUtil.errorResponse("Bad Request", "Empty request body", 400))
          .build();
    }

    try {
      JsonObject json = JsonParser.parseString(requestBody).getAsJsonObject();

      if (!json.has("content")) {
        return ProcessorResponse.builder()
            .statusCode(400)
            .body(JsonUtil.errorResponse("Bad Request", "Missing required field: content", 400))
            .build();
      }

      String content = json.get("content").getAsString();
      Module module = moduleManager.updateModule(operation, content);

      return ProcessorResponse.builder()
          .statusCode(200)
          .body(JsonUtil.successResponse(module))
          .build();
    } catch (IOException e) {
      logger.error("Failed to update module: {}", operation, e);
      return ProcessorResponse.builder()
          .statusCode(404)
          .body(JsonUtil.errorResponse("Not Found", e.getMessage(), 404))
          .build();
    } catch (Exception e) {
      logger.error("Unexpected error updating module: {}", operation, e);
      return ProcessorResponse.builder()
          .statusCode(500)
          .body(
              JsonUtil.errorResponse(
                  "Internal Server Error", "Failed to update module: " + e.getMessage(), 500))
          .build();
    }
  }

  private ProcessorResponse handleDelete(String operation, HttpServletRequest request)
      throws IOException {
    try {
      moduleManager.deleteModule(operation);

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("message", "Module deleted successfully");
      responseData.put("path", operation);

      return ProcessorResponse.builder()
          .statusCode(200)
          .body(JsonUtil.successResponse(responseData))
          .build();
    } catch (IOException e) {
      logger.error("Failed to delete module: {}", operation, e);
      return ProcessorResponse.builder()
          .statusCode(404)
          .body(JsonUtil.errorResponse("Not Found", e.getMessage(), 404))
          .build();
    } catch (Exception e) {
      logger.error("Unexpected error deleting module: {}", operation, e);
      return ProcessorResponse.builder()
          .statusCode(500)
          .body(
              JsonUtil.errorResponse(
                  "Internal Server Error", "Failed to delete module: " + e.getMessage(), 500))
          .build();
    }
  }

  private String readRequestBody(HttpServletRequest request) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = request.getReader()) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
    }
    return sb.toString();
  }
}
