package com.example.servlet.processor;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ApiScriptProcessorDebugTest {
  public static void main(String[] args) throws Exception {
    ApiScriptProcessor processor = new ApiScriptProcessor();

    // Create test scripts directory
    Path testScriptsDir = Paths.get("scripts/api");
    Files.createDirectories(testScriptsDir);

    // Create a simple test script
    String script =
        """
        function httpHandler(request, response) {
          response.setStatus(200);
          response.setBody(JSON.stringify({ message: 'Hello, World!' }));
        }
        """;

    Path scriptPath = testScriptsDir.resolve("debug-test.js");
    Files.writeString(scriptPath, script);

    try {
      Map<String, String> queryParams = new HashMap<>();
      Map<String, String> headers = new HashMap<>();
      JsonObject body = new JsonObject();

      Map<String, Object> result =
          processor.processRequest(
              "debug-test", "GET", "/api/v1/debug-test", queryParams, headers, body);

      System.out.println("Status: " + result.get("status"));
      System.out.println("Body: " + result.get("body"));
    } finally {
      Files.deleteIfExists(scriptPath);
    }
  }
}
