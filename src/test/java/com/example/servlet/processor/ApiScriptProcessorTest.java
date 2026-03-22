package com.example.servlet.processor;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiScriptProcessorTest {

  private ApiScriptProcessor processor;
  private Path testScriptsDir;

  @BeforeAll
  void setUp() throws IOException {
    processor = new ApiScriptProcessor();

    // Create test scripts directory
    testScriptsDir = Paths.get("scripts/api");
    Files.createDirectories(testScriptsDir);
  }

  @AfterAll
  void tearDown() throws IOException {
    // Clean up test scripts (optional, as they're useful examples)
  }

  @Test
  void testProcessRequest_HelloEndpoint() throws IOException {
    // Create a simple test script
    String script =
        """
        function httpHandler(request, response) {
          response.setStatus(200);
          response.setBody(JSON.stringify({ message: 'Hello, World!' }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test-hello.js");
    Files.writeString(scriptPath, script);

    try {
      Map<String, String> queryParams = new HashMap<>();
      Map<String, String> headers = new HashMap<>();
      JsonObject body = new JsonObject();

      Map<String, Object> result =
          processor.processRequest(
              "test-hello", "GET", "/api/v1/test-hello", queryParams, headers, body);

      assertEquals(200, result.get("status"));
      String responseBody = (String) result.get("body");
      assertNotNull(responseBody);
      assertTrue(responseBody.contains("Hello, World!"));
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }

  @Test
  void testProcessRequest_WithQueryParams() throws IOException {
    String script =
        """
        function httpHandler(request, response) {
          var name = request.queryParams.name || 'Guest';
          response.setStatus(200);
          response.setBody(JSON.stringify({ greeting: 'Hello, ' + name }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test-query.js");
    Files.writeString(scriptPath, script);

    try {
      Map<String, String> queryParams = new HashMap<>();
      queryParams.put("name", "Alice");
      Map<String, String> headers = new HashMap<>();
      JsonObject body = new JsonObject();

      Map<String, Object> result =
          processor.processRequest(
              "test-query", "GET", "/api/v1/test-query", queryParams, headers, body);

      assertEquals(200, result.get("status"));
      String responseBody = (String) result.get("body");
      assertTrue(responseBody.contains("Alice"));
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }

  @Test
  void testProcessRequest_WithRequestBody() throws IOException {
    String script =
        """
        function httpHandler(request, response) {
          var body = request.body;
          response.setStatus(201);
          response.setBody(JSON.stringify({
            message: 'Created',
            received: body
          }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test-post.js");
    Files.writeString(scriptPath, script);

    try {
      Map<String, String> queryParams = new HashMap<>();
      Map<String, String> headers = new HashMap<>();
      JsonObject body = new JsonObject();
      body.addProperty("name", "Test");
      body.addProperty("value", 123);

      Map<String, Object> result =
          processor.processRequest(
              "test-post", "POST", "/api/v1/test-post", queryParams, headers, body);

      assertEquals(201, result.get("status"));
      String responseBody = (String) result.get("body");
      assertTrue(responseBody.contains("Test"));
      assertTrue(responseBody.contains("123"));
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }

  @Test
  void testProcessRequest_WithCustomHeaders() throws IOException {
    String script =
        """
        function httpHandler(request, response) {
          response.setStatus(200);
          response.setHeader('X-Custom-Header', 'test-value');
          response.setHeader('X-API-Version', '1.0');
          response.setBody(JSON.stringify({ status: 'ok' }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test-headers.js");
    Files.writeString(scriptPath, script);

    try {
      Map<String, String> queryParams = new HashMap<>();
      Map<String, String> headers = new HashMap<>();
      JsonObject body = new JsonObject();

      Map<String, Object> result =
          processor.processRequest(
              "test-headers", "GET", "/api/v1/test-headers", queryParams, headers, body);

      assertEquals(200, result.get("status"));

      @SuppressWarnings("unchecked")
      Map<String, String> responseHeaders = (Map<String, String>) result.get("headers");
      assertEquals("test-value", responseHeaders.get("X-Custom-Header"));
      assertEquals("1.0", responseHeaders.get("X-API-Version"));
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }

  @Test
  void testProcessRequest_RequireModule() throws IOException {
    // Create a library module
    Path libDir = Paths.get("scripts/lib");
    Files.createDirectories(libDir);
    Path libPath = libDir.resolve("test-utils.js");

    String libScript =
        """
        function greet(name) {
          return 'Hello, ' + name + '!';
        }
        module.exports = { greet: greet };
        """;
    Files.writeString(libPath, libScript);

    // Create API script that uses the library
    String script =
        """
        var utils = require('../lib/test-utils.js');
        function httpHandler(request, response) {
          var greeting = utils.greet('World');
          response.setStatus(200);
          response.setBody(JSON.stringify({ message: greeting }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test-require.js");
    Files.writeString(scriptPath, script);

    try {
      Map<String, String> queryParams = new HashMap<>();
      Map<String, String> headers = new HashMap<>();
      JsonObject body = new JsonObject();

      Map<String, Object> result =
          processor.processRequest(
              "test-require", "GET", "/api/v1/test-require", queryParams, headers, body);

      assertEquals(200, result.get("status"));
      String responseBody = (String) result.get("body");
      assertTrue(responseBody.contains("Hello, World!"));
    } finally {
      Files.deleteIfExists(scriptPath);
      Files.deleteIfExists(libPath);
    }
  }

  @Test
  void testProcessRequest_ScriptNotFound() {
    Map<String, String> queryParams = new HashMap<>();
    Map<String, String> headers = new HashMap<>();
    JsonObject body = new JsonObject();

    Map<String, Object> result =
        processor.processRequest(
            "nonexistent", "GET", "/api/v1/nonexistent", queryParams, headers, body);

    assertEquals(404, result.get("status"));
    String responseBody = (String) result.get("body");
    assertTrue(responseBody.contains("not found"));
  }

  @Test
  void testProcessRequest_ScriptError() throws IOException {
    String script =
        """
        function httpHandler(request, response) {
          // This will cause an error
          throw new Error('Test error');
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test-error.js");
    Files.writeString(scriptPath, script);

    try {
      Map<String, String> queryParams = new HashMap<>();
      Map<String, String> headers = new HashMap<>();
      JsonObject body = new JsonObject();

      Map<String, Object> result =
          processor.processRequest(
              "test-error", "GET", "/api/v1/test-error", queryParams, headers, body);

      assertEquals(400, result.get("status"));
      String responseBody = (String) result.get("body");
      assertTrue(responseBody.contains("error") || responseBody.contains("Error"));
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }

  @Test
  void testProcessRequest_SecurityRestrictions() throws IOException {
    // Test that scripts cannot access restricted classes
    String script =
        """
        function httpHandler(request, response) {
          // Try to access restricted java.io.File
          var File = Java.type('java.io.File');
          response.setStatus(200);
          response.setBody(JSON.stringify({ message: 'Should not reach here' }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test-security.js");
    Files.writeString(scriptPath, script);

    try {
      Map<String, String> queryParams = new HashMap<>();
      Map<String, String> headers = new HashMap<>();
      JsonObject body = new JsonObject();

      Map<String, Object> result =
          processor.processRequest(
              "test-security", "GET", "/api/v1/test-security", queryParams, headers, body);

      // Should return an error status, not 200
      assertNotEquals(200, result.get("status"));
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }

  @Test
  void testProcessRequest_DifferentHttpMethods() throws IOException {
    String script =
        """
        function httpHandler(request, response) {
          response.setStatus(200);
          response.setBody(JSON.stringify({ method: request.method }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("test-methods.js");
    Files.writeString(scriptPath, script);

    try {
      String[] methods = {"GET", "POST", "PUT", "DELETE", "PATCH"};

      for (String method : methods) {
        Map<String, String> queryParams = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        JsonObject body = new JsonObject();

        Map<String, Object> result =
            processor.processRequest(
                "test-methods", method, "/api/v1/test-methods", queryParams, headers, body);

        assertEquals(200, result.get("status"));
        String responseBody = (String) result.get("body");
        assertTrue(responseBody.contains(method));
      }
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }
}
