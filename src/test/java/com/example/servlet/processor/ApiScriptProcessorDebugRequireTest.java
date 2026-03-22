package com.example.servlet.processor;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ApiScriptProcessorDebugRequireTest {
  public static void main(String[] args) throws Exception {
    ApiScriptProcessor processor = new ApiScriptProcessor();

    // Create test scripts directory
    Path testScriptsDir = Paths.get("scripts/api");
    Files.createDirectories(testScriptsDir);

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

      System.out.println("Status: " + result.get("status"));
      System.out.println("Body: " + result.get("body"));
    } finally {
      Files.deleteIfExists(scriptPath);
      Files.deleteIfExists(libPath);
    }
  }
}
