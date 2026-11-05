package com.example.scantest.noctor;

import com.example.scheduler.JobHandler;
import com.example.scheduler.JobName;
import com.google.gson.JsonObject;

@JobName("needs-argument")
public class NeedsArgument implements JobHandler {
  public NeedsArgument(String required) {}

  @Override
  public JsonObject execute(JsonObject context) {
    return context;
  }
}
