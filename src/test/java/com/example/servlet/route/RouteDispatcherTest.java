package com.example.servlet.route;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.servlet.model.Route;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Test RouteDispatcher to verify proper response handling for all route types. */
public class RouteDispatcherTest {

  private RouteDispatcher dispatcher;
  private HttpServletRequest request;
  private HttpServletResponse response;

  @BeforeEach
  public void setUp() {
    dispatcher = new RouteDispatcher();
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
  }

  @Test
  public void testStaticFileRoute() throws IOException {
    // Given: Static file route for /script-editor
    RouteRegistry.RouteMatch match = RouteRegistry.getInstance().findRoute("GET", "/script-editor");
    assertNotNull(match, "Route should be found");
    assertEquals("static", match.getRoute().getType());

    // Mock response to capture output
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    when(response.getOutputStream())
        .thenReturn(
            new jakarta.servlet.ServletOutputStream() {
              @Override
              public void write(int b) {
                outputStream.write(b);
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setWriteListener(jakarta.servlet.WriteListener writeListener) {}
            });

    // When: Dispatch the request
    boolean handled = dispatcher.dispatch(match, request, response);

    // Then: Response is handled
    assertTrue(handled, "Route should be handled by dispatcher");
    verify(response).setContentType("text/html");
    verify(response).setCharacterEncoding("UTF-8");
    assertTrue(outputStream.size() > 0, "Static file content should be written");
  }

  @Test
  public void testStaticFileNotFound() throws IOException {
    // Given: Route with non-existent resource
    Route route = new Route();
    route.setPath("/nonexistent");
    route.setType("static");
    route.setResource("static/nonexistent.html");
    route.setContentType("text/html");

    Map<String, String> pathParams = new HashMap<>();
    RouteRegistry.RouteMatch match = new RouteRegistry.RouteMatch(route, pathParams);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // When: Dispatch the request
    boolean handled = dispatcher.dispatch(match, request, response);

    // Then: Returns 404 error
    assertTrue(handled);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    verify(response).setContentType("application/json");
    writer.flush();
    String output = stringWriter.toString();
    assertTrue(output.contains("\"error\":\"File not found\""));
    assertTrue(output.contains("\"status\":404"));
  }

  @Test
  public void testHandlerRouteWithoutParams() throws IOException {
    // Given: Handler route for /api/attachments
    RouteRegistry.RouteMatch match =
        RouteRegistry.getInstance().findRoute("GET", "/api/attachments");
    assertNotNull(match, "Route should be found");
    assertEquals("handler", match.getRoute().getType());
    assertEquals("AttachmentHandler", match.getRoute().getHandler());

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // When: Dispatch the request
    boolean handled = dispatcher.dispatch(match, request, response);

    // Then: Response is handled
    assertTrue(handled, "Route should be handled by dispatcher");
    verify(response, atLeastOnce()).setContentType("application/json");
    verify(response, atLeastOnce()).setCharacterEncoding("UTF-8");
    writer.flush();
    String output = stringWriter.toString();
    assertTrue(output.contains("\"attachments\""), "Should return attachments list");
  }

  @Test
  public void testHandlerRouteWithPathParams() throws IOException {
    // Given: Handler route for /api/attachment/{id}
    RouteRegistry.RouteMatch match =
        RouteRegistry.getInstance().findRoute("GET", "/api/attachment/test-123");
    assertNotNull(match, "Route should be found");
    assertEquals("handler", match.getRoute().getType());
    assertEquals("AttachmentHandler", match.getRoute().getHandler());
    assertEquals("test-123", match.getPathParam("id"));

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // When: Dispatch the request
    boolean handled = dispatcher.dispatch(match, request, response);

    // Then: Response is handled
    assertTrue(handled, "Route should be handled by dispatcher");
    verify(response, atLeastOnce()).setContentType("application/json");
    verify(response, atLeastOnce()).setCharacterEncoding("UTF-8");
    writer.flush();
    String output = stringWriter.toString();
    // Should return error since attachment doesn't exist, but response is proper JSON
    assertTrue(output.contains("\"error\"") || output.contains("\"id\""));
  }

  @Test
  public void testHandlerRouteDownload() throws IOException {
    // Given: Handler route for /api/attachment/{id}/download
    RouteRegistry.RouteMatch match =
        RouteRegistry.getInstance().findRoute("GET", "/api/attachment/test-456/download");
    assertNotNull(match, "Route should be found");
    assertEquals("handler", match.getRoute().getType());
    assertEquals("AttachmentHandler", match.getRoute().getHandler());
    assertEquals("handleDownload", match.getRoute().getHandlerMethod());
    assertEquals("test-456", match.getPathParam("id"));

    // Mock request for handleDownload signature
    when(request.getPathInfo()).thenReturn("/api/attachment/test-456/download");

    // Mock response writer for error response (attachment doesn't exist)
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // When: Dispatch the request
    boolean handled = dispatcher.dispatch(match, request, response);

    // Then: Response is handled (returns error since attachment doesn't exist)
    assertTrue(handled, "Route should be handled by dispatcher");
    writer.flush();
    String output = stringWriter.toString();
    assertTrue(output.contains("\"error\""), "Should return error for non-existent attachment");
  }

  @Test
  public void testProcessorRouteModules() throws IOException {
    // Given: Processor route for /api/modules/**
    RouteRegistry.RouteMatch match =
        RouteRegistry.getInstance().findRoute("GET", "/api/modules/list");
    assertNotNull(match, "Route should be found");
    assertEquals("processor", match.getRoute().getType());
    assertEquals("ModuleProcessor", match.getRoute().getProcessor());

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);
    when(request.getMethod()).thenReturn("GET");
    when(request.getPathInfo()).thenReturn("/api/modules/list");

    // When: Dispatch the request
    boolean handled = dispatcher.dispatch(match, request, response);

    // Then: Response is handled
    assertTrue(handled, "Route should be handled by dispatcher");
    verify(response, atLeastOnce()).setContentType("application/json");
    verify(response).setCharacterEncoding("UTF-8");
    writer.flush();
    String output = stringWriter.toString();
    assertTrue(output.contains("\"modules\""), "Should return modules list");
  }

  @Test
  public void testBuiltinRouteReturnsHand() throws IOException {
    // Given: Builtin route for /health
    RouteRegistry.RouteMatch match = RouteRegistry.getInstance().findRoute("GET", "/health");
    assertNotNull(match, "Route should be found");
    assertEquals("builtin", match.getRoute().getType());
    assertEquals("handleHealth", match.getRoute().getHandler());

    // When: Dispatch the request
    boolean handled = dispatcher.dispatch(match, request, response);

    // Then: Returns false (not handled by dispatcher, RouterServlet handles it)
    assertFalse(handled, "Builtin routes should return false, handled by RouterServlet");
  }

  @Test
  public void testDeleteHandlerRoute() throws IOException {
    // Given: DELETE handler route for /api/attachment/{id}
    RouteRegistry.RouteMatch match =
        RouteRegistry.getInstance().findRoute("DELETE", "/api/attachment/test-789");
    assertNotNull(match, "Route should be found");
    assertEquals("handler", match.getRoute().getType());
    assertEquals("AttachmentHandler", match.getRoute().getHandler());
    assertEquals("handleDelete", match.getRoute().getHandlerMethod());
    assertEquals("test-789", match.getPathParam("id"));

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // When: Dispatch the request
    boolean handled = dispatcher.dispatch(match, request, response);

    // Then: Response is handled
    assertTrue(handled, "Route should be handled by dispatcher");
    verify(response, atLeastOnce()).setContentType("application/json");
    verify(response, atLeastOnce()).setCharacterEncoding("UTF-8");
    writer.flush();
    String output = stringWriter.toString();
    assertTrue(output.contains("\"error\"") || output.contains("\"success\""));
  }

  @Test
  public void testDataBrowserHandlerRoute() throws IOException {
    // Given: POST handler route for /api/data-browser/driver-status
    RouteRegistry.RouteMatch match =
        RouteRegistry.getInstance().findRoute("POST", "/api/data-browser/driver-status");
    assertNotNull(match, "Route should be found");
    assertEquals("handler", match.getRoute().getType());
    assertEquals("DataBrowserHandler", match.getRoute().getHandler());
    assertEquals("handleDriverStatus", match.getRoute().getHandlerMethod());

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    // Mock request body for handleDriverStatus
    when(request.getReader())
        .thenReturn(
            new java.io.BufferedReader(new java.io.StringReader("{\"dbType\":\"postgresql\"}")));

    // When: Dispatch the request
    boolean handled = dispatcher.dispatch(match, request, response);

    // Then: Response is handled
    assertTrue(handled, "Route should be handled by dispatcher");
    verify(response, atLeastOnce()).setContentType("application/json");
    verify(response, atLeastOnce()).setCharacterEncoding("UTF-8");
    writer.flush();
    String output = stringWriter.toString();
    assertTrue(output.contains("\"downloaded\""), "Should return driver status");
  }

  @Test
  public void testStaticDataBrowserRoute() throws IOException {
    // Given: Static file route for /data-browser
    RouteRegistry.RouteMatch match = RouteRegistry.getInstance().findRoute("GET", "/data-browser");
    assertNotNull(match, "Route should be found");
    assertEquals("static", match.getRoute().getType());

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    when(response.getOutputStream())
        .thenReturn(
            new jakarta.servlet.ServletOutputStream() {
              @Override
              public void write(int b) {
                outputStream.write(b);
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setWriteListener(jakarta.servlet.WriteListener writeListener) {}
            });

    // When: Dispatch the request
    boolean handled = dispatcher.dispatch(match, request, response);

    // Then: Response is handled
    assertTrue(handled, "Route should be handled by dispatcher");
    verify(response).setContentType("text/html");
    verify(response).setCharacterEncoding("UTF-8");
    assertTrue(outputStream.size() > 0, "Data browser HTML should be written");
  }

  @Test
  public void testModulePOSTRoute() throws IOException {
    // Given: POST processor route for /api/modules/create
    RouteRegistry.RouteMatch match =
        RouteRegistry.getInstance().findRoute("POST", "/api/modules/create");
    assertNotNull(match, "Route should be found");
    assertEquals("processor", match.getRoute().getType());
    assertEquals("ModuleProcessor", match.getRoute().getProcessor());

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);
    when(request.getMethod()).thenReturn("POST");
    when(request.getPathInfo()).thenReturn("/api/modules/create");

    // Mock request body
    String jsonBody = "{\"name\":\"test\",\"content\":\"console.log('test');\"}";
    when(request.getReader())
        .thenReturn(new java.io.BufferedReader(new java.io.StringReader(jsonBody)));

    // When: Dispatch the request
    boolean handled = dispatcher.dispatch(match, request, response);

    // Then: Response is handled
    assertTrue(handled, "Route should be handled by dispatcher");
    verify(response, atLeastOnce()).setContentType("application/json");
    verify(response).setCharacterEncoding("UTF-8");
    writer.flush();
    String output = stringWriter.toString();
    assertTrue(output.contains("\"status\"") || output.contains("\"error\""));
  }
}
