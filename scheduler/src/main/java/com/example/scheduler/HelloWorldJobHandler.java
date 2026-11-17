package com.example.scheduler;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sample Java handler. Logs a greeting and counts its runs. */
@JobName("hello-world")
public class HelloWorldJobHandler implements JobHandler {

  private static final Logger logger = LoggerFactory.getLogger(HelloWorldJobHandler.class);

  @Override
  public JsonObject execute(JsonObject context) {
    int runs = context.has("runs") ? context.get("runs").getAsInt() + 1 : 1;
    logger.info("Hello World from a Java job (run {})", runs);
    context.addProperty("runs", runs);
    return context;
  }
}
