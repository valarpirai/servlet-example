package com.example.scheduler;

import com.example.processor.ApiScriptProcessor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a stored script as a job. Calls the script's {@code run(request, response)} function. The
 * job context arrives as {@code request.body}. A response status of 400 or more fails the run.
 */
public class ScriptJobHandler implements JobHandler {

  private static final Logger logger = LoggerFactory.getLogger(ScriptJobHandler.class);
  private static final String FUNCTION = "run";

  private final ApiScriptProcessor processor;
  private final String scriptName;

  /**
   * @param processor runs the script
   * @param scriptName name of the {@code api_scripts} row
   */
  public ScriptJobHandler(ApiScriptProcessor processor, String scriptName) {
    this.processor = processor;
    this.scriptName = scriptName;
  }

  @Override
  public JsonObject execute(JsonObject context) throws Exception {
    Map<String, Object> response =
        processor.processRoutedRequest(
            scriptName,
            FUNCTION,
            Map.of(),
            "JOB",
            "/jobs/" + scriptName,
            Map.of(),
            Map.of(),
            context);
    int status = ((Number) response.get("status")).intValue();
    if (status >= 400) {
      throw new IllegalStateException(
          "script " + scriptName + " failed: " + errorMessage(response.get("body")));
    }
    logger.info("Script job {} responded: {}", scriptName, response.get("body"));
    return context;
  }

  /** Pulls {@code message} out of the processor's error JSON. Falls back to the raw body. */
  private static String errorMessage(Object body) {
    try {
      return JsonParser.parseString(String.valueOf(body))
          .getAsJsonObject()
          .get("message")
          .getAsString();
    } catch (RuntimeException e) {
      return String.valueOf(body);
    }
  }
}
