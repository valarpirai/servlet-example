package com.example.scantest.dup;

import com.example.scheduler.JobHandler;
import com.example.scheduler.JobName;
import com.google.gson.JsonObject;

@JobName("same")
public class FirstHandler implements JobHandler {
  @Override
  public JsonObject execute(JsonObject context) {
    return context;
  }
}
