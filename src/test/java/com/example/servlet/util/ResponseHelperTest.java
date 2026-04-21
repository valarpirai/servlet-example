package com.example.servlet.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.servlet.model.ProcessorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResponseHelperTest {

  @Mock private HttpServletResponse response;

  // --- ProcessorResponse factories ---

  @Test
  void errorResponse_setsCorrectStatusAndContentType() {
    ProcessorResponse r = ResponseHelper.errorResponse(400, "Bad Request", "missing field");
    assertEquals(400, r.getStatusCode());
    assertEquals("application/json", r.getContentType());
    assertTrue(r.getBody().contains("Bad Request"));
    assertTrue(r.getBody().contains("missing field"));
  }

  @Test
  void successResponse_setsStatus200() {
    ProcessorResponse r = ResponseHelper.successResponse(Map.of("id", 1));
    assertEquals(200, r.getStatusCode());
    assertEquals("application/json", r.getContentType());
  }

  @Test
  void badRequest_returns400() {
    assertEquals(400, ResponseHelper.badRequest("oops").getStatusCode());
  }

  @Test
  void notFound_returns404() {
    assertEquals(404, ResponseHelper.notFound("not here").getStatusCode());
  }

  @Test
  void internalError_returns500() {
    assertEquals(500, ResponseHelper.internalError("crash").getStatusCode());
  }

  @Test
  void badRequest_bodyContainsMessage() {
    assertTrue(ResponseHelper.badRequest("my error").getBody().contains("my error"));
  }

  // --- sendJsonError ---

  @Test
  void sendJsonError_setsStatusAndContentType() throws IOException {
    StringWriter sw = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(sw));

    ResponseHelper.sendJsonError(response, 404, "Not Found", "gone");

    verify(response).setStatus(404);
    verify(response).setContentType("application/json");
    assertTrue(sw.toString().contains("Not Found"));
    assertTrue(sw.toString().contains("gone"));
  }

  @Test
  void sendNotFound_sends404() throws IOException {
    StringWriter sw = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(sw));

    ResponseHelper.sendNotFound(response, "missing");

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void sendBadRequest_sends400() throws IOException {
    StringWriter sw = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(sw));

    ResponseHelper.sendBadRequest(response, "bad");

    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  // --- sendJsonSuccess ---

  @Test
  void sendJsonSuccess_setsStatus200AndContentType() throws IOException {
    StringWriter sw = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(sw));

    ResponseHelper.sendJsonSuccess(response, Map.of("ok", true));

    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setContentType("application/json");
  }

  @Test
  void sendJsonSuccess_writesBody() throws IOException {
    StringWriter sw = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(sw));

    ResponseHelper.sendJsonSuccess(response, Map.of("result", "done"));

    assertTrue(sw.toString().contains("done"));
  }
}
