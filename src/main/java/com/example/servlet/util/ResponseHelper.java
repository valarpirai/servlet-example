package com.example.servlet.util;

import com.example.servlet.model.ProcessorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Helper class for common response patterns to reduce code duplication. */
public class ResponseHelper {

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
