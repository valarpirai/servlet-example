package com.example.servlet.processor;

import com.example.servlet.model.ProcessorResponse;
import com.example.servlet.util.RequestHelper;
import com.example.servlet.util.ResponseHelper;
import com.example.servlet.util.TemplateEngine;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TemplateProcessor implements IRequestProcessor {

  private static final String CONTENT_TYPE = "text/html";

  @Override
  public boolean supports(String contentType) {
    return contentType != null && contentType.toLowerCase().startsWith("text/html");
  }

  @Override
  public ProcessorResponse process(HttpServletRequest request)
      throws IOException, ServletException {
    try {
      // Read and parse JSON
      JsonObject json = RequestHelper.readJsonBody(request);

      // Get template path or inline template
      String templatePath = null;
      String inlineTemplate = null;

      if (json.has("templatePath")) {
        templatePath = json.get("templatePath").getAsString();
      } else if (json.has("template")) {
        inlineTemplate = json.get("template").getAsString();
      } else {
        return ResponseHelper.badRequest("Missing 'templatePath' or 'template' field");
      }

      // Get data for template
      Map<String, Object> data = new HashMap<>();
      if (json.has("data") && json.get("data").isJsonObject()) {
        JsonObject dataJson = json.getAsJsonObject("data");
        data = RequestHelper.jsonObjectToMap(dataJson);
      }

      // Load or use template
      String templateContent;
      if (templatePath != null) {
        try {
          templateContent = TemplateEngine.loadTemplate("templates/" + templatePath);
        } catch (IOException e) {
          return ResponseHelper.notFound("Template not found: " + templatePath);
        }
      } else {
        templateContent = inlineTemplate;
      }

      // Render template
      String renderedHtml = TemplateEngine.render(templateContent, data);

      // Return HTML response
      return ProcessorResponse.builder()
          .statusCode(200)
          .contentType("text/html; charset=UTF-8")
          .body(renderedHtml)
          .build();

    } catch (IllegalArgumentException e) {
      return ResponseHelper.badRequest(e.getMessage());
    } catch (com.google.gson.JsonSyntaxException e) {
      return ResponseHelper.badRequest("Invalid JSON: " + e.getMessage());
    } catch (Exception e) {
      return ResponseHelper.internalError("Error rendering template: " + e.getMessage());
    }
  }

  @Override
  public String getContentType() {
    return CONTENT_TYPE;
  }
}
