package com.example.scheduler;

import com.google.gson.JsonObject;

/** Code that a job runs. Registered under a key in {@link HandlerRegistry}. */
@FunctionalInterface
public interface JobHandler {

  /**
   * Runs the job once.
   *
   * @param context state saved by the previous run; empty on the first run
   * @return state to save for the next run
   * @throws Exception any failure; the scheduler records it and carries on
   */
  JsonObject execute(JsonObject context) throws Exception;
}
