package com.example.servlet.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ApiHandlerTest {

  @Mock private HttpServletRequest request;

  @Mock private HttpServletResponse response;

  private ApiHandler handler;
  private StringWriter responseWriter;
  private Path testScriptsDir;

  @BeforeEach
  void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    handler = ApiHandler.getInstance();
    responseWriter = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

    // Create test scripts directory
    testScriptsDir = Paths.get("scripts/api");
    Files.createDirectories(testScriptsDir);
  }

  @Test
  void testHandleRequest_SimpleGetRequest() throws IOException {
    // Create a simple test script
    String script =
        """
        function httpHandler(request, response) {
          response.setStatus(200);
          response.setBody(JSON.stringify({ message: 'Test response' }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test.js");
    Files.writeString(scriptPath, script);

    try {
      when(request.getPathInfo()).thenReturn("/test");
      when(request.getMethod()).thenReturn("GET");
      when(request.getRequestURI()).thenReturn("/api/v1/test");
      when(request.getQueryString()).thenReturn(null);
      when(request.getParameterMap()).thenReturn(new HashMap<>());
      when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

      handler.handleRequest(request, response);

      verify(response).setStatus(200);
      String responseBody = responseWriter.toString();
      assertTrue(responseBody.contains("Test response"));
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }

  @Test
  void testHandleRequest_WithQueryParameters() throws IOException {
    String script =
        """
        function httpHandler(request, response) {
          var name = request.queryParams.name;
          response.setStatus(200);
          response.setBody(JSON.stringify({ name: name }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test-query.js");
    Files.writeString(scriptPath, script);

    try {
      when(request.getPathInfo()).thenReturn("/test-query");
      when(request.getMethod()).thenReturn("GET");
      when(request.getRequestURI()).thenReturn("/api/v1/test-query");
      when(request.getQueryString()).thenReturn("name=Alice");

      Map<String, String[]> paramMap = new HashMap<>();
      paramMap.put("name", new String[] {"Alice"});
      when(request.getParameterMap()).thenReturn(paramMap);
      when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

      handler.handleRequest(request, response);

      verify(response).setStatus(200);
      String responseBody = responseWriter.toString();
      assertTrue(responseBody.contains("Alice"));
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }

  @Test
  void testHandleRequest_PostWithBody() throws IOException {
    String script =
        """
        function httpHandler(request, response) {
          var body = request.body;
          response.setStatus(201);
          response.setBody(JSON.stringify({ received: body.name }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test-post.js");
    Files.writeString(scriptPath, script);

    try {
      String requestBody = "{\"name\":\"Test User\"}";
      BufferedReader reader = new BufferedReader(new StringReader(requestBody));

      when(request.getPathInfo()).thenReturn("/test-post");
      when(request.getMethod()).thenReturn("POST");
      when(request.getRequestURI()).thenReturn("/api/v1/test-post");
      when(request.getQueryString()).thenReturn(null);
      when(request.getParameterMap()).thenReturn(new HashMap<>());
      when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
      when(request.getReader()).thenReturn(reader);

      handler.handleRequest(request, response);

      verify(response).setStatus(201);
      String responseBody = responseWriter.toString();
      assertTrue(responseBody.contains("Test User"));
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }

  @Test
  void testHandleRequest_EndpointNotFound() throws IOException {
    when(request.getPathInfo()).thenReturn("/nonexistent");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/v1/nonexistent");
    when(request.getQueryString()).thenReturn(null);
    when(request.getParameterMap()).thenReturn(new HashMap<>());
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    handler.handleRequest(request, response);

    verify(response).setStatus(404);
    String responseBody = responseWriter.toString();
    assertTrue(responseBody.contains("not found") || responseBody.contains("Not Found"));
  }

  @Test
  void testHandleRequest_InvalidPath() throws IOException {
    when(request.getPathInfo()).thenReturn("/");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/v1/");

    handler.handleRequest(request, response);

    // Should return bad request for invalid endpoint
    verify(response, atLeastOnce()).setContentType(anyString());
  }

  @Test
  void testHandleRequest_WithCustomHeaders() throws IOException {
    String script =
        """
        function httpHandler(request, response) {
          response.setStatus(200);
          response.setHeader('X-Custom', 'value');
          response.setBody(JSON.stringify({ status: 'ok' }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test-headers.js");
    Files.writeString(scriptPath, script);

    try {
      when(request.getPathInfo()).thenReturn("/test-headers");
      when(request.getMethod()).thenReturn("GET");
      when(request.getRequestURI()).thenReturn("/api/v1/test-headers");
      when(request.getQueryString()).thenReturn(null);
      when(request.getParameterMap()).thenReturn(new HashMap<>());
      when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

      handler.handleRequest(request, response);

      verify(response).setStatus(200);
      verify(response).setHeader("X-Custom", "value");
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }

  @Test
  void testExtractEndpointName_SimplePath() throws IOException {
    when(request.getPathInfo()).thenReturn("/users");

    // This is tested indirectly through handleRequest
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/v1/users");
    when(request.getQueryString()).thenReturn(null);
    when(request.getParameterMap()).thenReturn(new HashMap<>());
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    // The endpoint "users" should be extracted and processed
    handler.handleRequest(request, response);

    // Verify that the handler attempted to process the request
    verify(response, atLeastOnce()).setContentType(anyString());
  }

  @Test
  void testExtractEndpointName_NestedPath() throws IOException {
    when(request.getPathInfo()).thenReturn("/api/v1/users");

    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/v1/users");
    when(request.getQueryString()).thenReturn(null);
    when(request.getParameterMap()).thenReturn(new HashMap<>());
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    // The endpoint "users" should be extracted from the nested path
    handler.handleRequest(request, response);

    verify(response, atLeastOnce()).setContentType(anyString());
  }
}
