package com.example.view;

import com.example.model.ProcessorResponse;
import com.example.util.JsonUtil;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Helper class for common response patterns to reduce code duplication. */
public class ResponseHelper {

  /**
   * Writes a body as JSON, UTF-8.
   *
   * @param response HTTP response object
   * @param status HTTP status code
   * @param body object written with {@link JsonUtil#toJson}
   * @throws IOException if writing response fails
   */
  public static void sendJson(HttpServletResponse response, int status, Object body)
      throws IOException {
    sendJsonText(response, status, JsonUtil.toJson(body));
  }

  /**
   * Writes text that is already JSON, UTF-8, exactly as given.
   *
   * @param response HTTP response object
   * @param status HTTP status code
   * @param json the response body
   * @throws IOException if writing response fails
   */
  public static void sendJsonText(HttpServletResponse response, int status, String json)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().print(json);
    response.getWriter().flush();
  }

  /**
   * Writes the table API error body, {@code {"error": message, "status": status}}.
   *
   * @param response HTTP response object
   * @param status HTTP status code
   * @param message error text
   * @throws IOException if writing response fails
   */
  public static void sendError(HttpServletResponse response, int status, String message)
      throws IOException {
    sendJson(response, status, java.util.Map.of("error", message, "status", status));
  }

  /**
   * Sends a JSON error response to the HTTP response.
   *
   * @param response HTTP response object
   * @param statusCode HTTP status code
   * @param error Error type
   * @param message Error message
   * @throws IOException if writing response fails
   */
  public static void sendJsonError(
      HttpServletResponse response, int statusCode, String error, String message)
      throws IOException {
    response.setStatus(statusCode);
    response.setContentType("application/json");
    response.getWriter().print(JsonUtil.errorResponse(error, message, statusCode));
  }

  /**
   * Sends a JSON success response to the HTTP response.
   *
   * @param response HTTP response object
   * @param data Response data
   * @throws IOException if writing response fails
   */
  public static void sendJsonSuccess(HttpServletResponse response, Object data) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.getWriter().print(JsonUtil.successResponse(data));
  }

  /**
   * Creates a ProcessorResponse for errors.
   *
   * @param statusCode HTTP status code
   * @param error Error type
   * @param message Error message
   * @return ProcessorResponse with error
   */
  public static ProcessorResponse errorResponse(int statusCode, String error, String message) {
    return ProcessorResponse.builder()
        .statusCode(statusCode)
        .contentType("application/json")
        .body(JsonUtil.errorResponse(error, message, statusCode))
        .build();
  }

  /**
   * Creates a ProcessorResponse for success.
   *
   * @param data Response data
   * @return ProcessorResponse with success data
   */
  public static ProcessorResponse successResponse(Object data) {
    return ProcessorResponse.builder()
        .statusCode(200)
        .contentType("application/json")
        .body(JsonUtil.successResponse(data))
        .build();
  }

  // Common error responses
  public static ProcessorResponse badRequest(String message) {
    return errorResponse(400, "Bad Request", message);
  }

  public static ProcessorResponse notFound(String message) {
    return errorResponse(404, "Not Found", message);
  }

  public static ProcessorResponse internalError(String message) {
    return errorResponse(500, "Internal Server Error", message);
  }

  public static void sendNotFound(HttpServletResponse response, String message) throws IOException {
    sendJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Not Found", message);
  }

  public static void sendBadRequest(HttpServletResponse response, String message)
      throws IOException {
    sendJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Bad Request", message);
  }
}
