package com.example.processor;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiScriptProcessorTest {

  private FakeScriptDb db;
  private ApiScriptProcessor processor;

  @BeforeEach
  void setUp() {
    db = new FakeScriptDb();
    processor = new ApiScriptProcessor(db);
  }

  private Map<String, Object> call(String endpoint, String method, JsonObject body) {
    return processor.processRequest(
        endpoint, method, "/api/v1/" + endpoint, new HashMap<>(), new HashMap<>(), body);
  }

  @Test
  void testProcessRequest_HelloEndpoint() {
    db.endpoint(
        "test-hello",
        """
        function httpHandler(request, response) {
          response.setStatus(200);
          response.setBody(JSON.stringify({ message: 'Hello, World!' }));
        }
        """);

    Map<String, Object> result = call("test-hello", "GET", new JsonObject());

    assertEquals(200, result.get("status"));
    assertTrue(((String) result.get("body")).contains("Hello, World!"));
  }

  @Test
  void testProcessRequest_WithQueryParams() {
    db.endpoint(
        "test-query",
        """
        function httpHandler(request, response) {
          var name = request.queryParams.name || 'Guest';
          response.setStatus(200);
          response.setBody(JSON.stringify({ greeting: 'Hello, ' + name }));
        }
        """);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("name", "Alice");
    Map<String, Object> result =
        processor.processRequest(
            "test-query",
            "GET",
            "/api/v1/test-query",
            queryParams,
            new HashMap<>(),
            new JsonObject());

    assertEquals(200, result.get("status"));
    assertTrue(((String) result.get("body")).contains("Alice"));
  }

  @Test
  void testProcessRequest_WithRequestBody() {
    db.endpoint(
        "test-post",
        """
        function httpHandler(request, response) {
          var body = request.body;
          response.setStatus(201);
          response.setBody(JSON.stringify({
            message: 'Created',
            received: body
          }));
        }
        """);

    JsonObject body = new JsonObject();
    body.addProperty("name", "Test");
    body.addProperty("value", 123);
    Map<String, Object> result = call("test-post", "POST", body);

    assertEquals(201, result.get("status"));
    String responseBody = (String) result.get("body");
    assertTrue(responseBody.contains("Test"));
    assertTrue(responseBody.contains("123"));
  }

  @Test
  void testProcessRequest_WithCustomHeaders() {
    db.endpoint(
        "test-headers",
        """
        function httpHandler(request, response) {
          response.setStatus(200);
          response.setHeader('X-Custom-Header', 'test-value');
          response.setHeader('X-API-Version', '1.0');
          response.setBody(JSON.stringify({ status: 'ok' }));
        }
        """);

    Map<String, Object> result = call("test-headers", "GET", new JsonObject());

    assertEquals(200, result.get("status"));
    @SuppressWarnings("unchecked")
    Map<String, String> responseHeaders = (Map<String, String>) result.get("headers");
    assertEquals("test-value", responseHeaders.get("X-Custom-Header"));
    assertEquals("1.0", responseHeaders.get("X-API-Version"));
  }

  @Test
  void testProcessRequest_ImportModule() {
    db.module(
        "lib/test-utils",
        """
        function greet(name) {
          return 'Hello, ' + name + '!';
        }
        module.exports = { greet: greet };
        """);
    db.endpoint(
        "test-import",
        """
        import utils from 'lib/test-utils';
        function httpHandler(request, response) {
          var greeting = utils.greet('World');
          response.setStatus(200);
          response.setBody(JSON.stringify({ message: greeting }));
        }
        """);

    Map<String, Object> result = call("test-import", "GET", new JsonObject());

    assertEquals(200, result.get("status"));
    assertTrue(((String) result.get("body")).contains("Hello, World!"));
  }

  @Test
  void testProcessRequest_ImportOfMissingModuleFails() {
    db.endpoint(
        "test-missing-import",
        """
        import nothing from 'lib/not-there';
        function httpHandler(request, response) {
          response.setStatus(200);
        }
        """);

    Map<String, Object> result = call("test-missing-import", "GET", new JsonObject());

    assertNotEquals(200, result.get("status"));
    assertTrue(((String) result.get("body")).contains("not-there"));
  }

  @Test
  void testProcessRequest_ScriptNotFound() {
    Map<String, Object> result = call("nonexistent", "GET", new JsonObject());

    assertEquals(404, result.get("status"));
    assertTrue(((String) result.get("body")).contains("not found"));
  }

  @Test
  void testProcessRequest_ScriptError() {
    db.endpoint(
        "test-error",
        """
        function httpHandler(request, response) {
          // This will cause an error
          throw new Error('Test error');
        }
        """);

    Map<String, Object> result = call("test-error", "GET", new JsonObject());

    assertEquals(400, result.get("status"));
    String responseBody = (String) result.get("body");
    assertTrue(responseBody.contains("error") || responseBody.contains("Error"));
  }

  @Test
  void testProcessRequest_SecurityRestrictions() {
    db.endpoint(
        "test-security",
        """
        function httpHandler(request, response) {
          // Try to access restricted java.io.File
          var File = Java.type('java.io.File');
          response.setStatus(200);
          response.setBody(JSON.stringify({ message: 'Should not reach here' }));
        }
        """);

    Map<String, Object> result = call("test-security", "GET", new JsonObject());

    // Should return an error status, not 200
    assertNotEquals(200, result.get("status"));
  }

  @Test
  void testProcessRequest_DifferentHttpMethods() {
    db.endpoint(
        "test-methods",
        """
        function httpHandler(request, response) {
          response.setStatus(200);
          response.setBody(JSON.stringify({ method: request.method }));
        }
        """);

    for (String method : new String[] {"GET", "POST", "PUT", "DELETE", "PATCH"}) {
      Map<String, Object> result = call("test-methods", method, new JsonObject());
      assertEquals(200, result.get("status"));
      assertTrue(((String) result.get("body")).contains(method));
    }
  }

  @Test
  void testProcessRequest_EndpointNameIsNotAPath() {
    db.endpoint("secret", "function httpHandler(request, response) { response.setStatus(200); }");

    // A traversal-style name is just a name that matches no row
    Map<String, Object> result = call("../secret", "GET", new JsonObject());

    assertEquals(404, result.get("status"));
  }

  // --- routed scripts: a named function and path params ---

  private Map<String, Object> callRouted(
      String script, String function, Map<String, String> pathParams) {
    return processor.processRoutedRequest(
        script,
        function,
        pathParams,
        "GET",
        "/users/42",
        new HashMap<>(),
        new HashMap<>(),
        new JsonObject());
  }

  @Test
  void testRoutedRequest_CallsTheNamedFunction() {
    db.endpoint(
        "multi",
        """
        function first(request, response) {
          response.setBody(JSON.stringify({ ran: 'first' }));
        }
        function second(request, response) {
          response.setBody(JSON.stringify({ ran: 'second' }));
        }
        """);

    Map<String, Object> result = callRouted("multi", "second", Map.of());

    assertEquals(200, result.get("status"));
    assertTrue(((String) result.get("body")).contains("second"));
  }

  @Test
  void testRoutedRequest_HandlerSeesPathParams() {
    db.endpoint(
        "show-user",
        """
        function show(request, response) {
          response.setBody(JSON.stringify({ id: request.pathParams.id }));
        }
        """);

    Map<String, Object> result = callRouted("show-user", "show", Map.of("id", "42"));

    assertTrue(((String) result.get("body")).contains("42"), (String) result.get("body"));
  }

  @Test
  void testRoutedRequest_HandlerGetsRequestAndResponseObjects() {
    db.endpoint(
        "both",
        """
        function handle(request, response) {
          response.setStatus(202);
          response.setHeader('X-Method', request.method);
          response.setBody('ok');
        }
        """);

    Map<String, Object> result = callRouted("both", "handle", Map.of());

    assertEquals(202, result.get("status"));
    @SuppressWarnings("unchecked")
    Map<String, String> headers = (Map<String, String>) result.get("headers");
    assertEquals("GET", headers.get("X-Method"));
  }

  @Test
  void testRoutedRequest_MissingFunctionIsAnError() {
    db.endpoint("empty", "var x = 1;");

    Map<String, Object> result = callRouted("empty", "handle", Map.of());

    assertEquals(500, result.get("status"));
    assertTrue(((String) result.get("body")).contains("Handler function not found: handle"));
  }

  @Test
  void testRoutedRequest_MissingScriptIs404() {
    Map<String, Object> result = callRouted("nope", "handle", Map.of());

    assertEquals(404, result.get("status"));
  }

  @Test
  void testEndpointRequest_StillToleratesAScriptWithoutHttpHandler() {
    db.endpoint("plain", "var x = 1;");

    Map<String, Object> result = call("plain", "GET", new JsonObject());

    assertEquals(200, result.get("status"));
  }
}
