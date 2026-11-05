package com.example.scantest.ok;

import com.example.scheduler.JobHandler;
import com.example.scheduler.JobName;
import com.google.gson.JsonObject;

@JobName("scan-ok")
public class OkHandler implements JobHandler {
  @Override
  public JsonObject execute(JsonObject context) {
    context.addProperty("ran", true);
    return context;
  }
}
