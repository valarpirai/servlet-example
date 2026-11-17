package com.example.scheduler;

import com.example.model.Job;
import com.example.record.AsyncRuleCall;
import com.example.record.AsyncRules;
import com.example.util.JsonUtil;
import com.example.util.PropertiesUtil;
import com.google.gson.JsonObject;

/**
 * Runs one async business rule. A write that matches an async rule saves the write in a job of this
 * handler. The job runs on a scheduler worker and retries on failure, so a script should be safe to
 * run twice.
 */
@JobName("business-rule")
public class BusinessRuleJob implements JobHandler {

  static final String HANDLER = "business-rule";

  @Override
  public JsonObject execute(JsonObject context) {
    AsyncRules.run(parse(context));
    return context;
  }

  /**
   * Reads a saved write back from a job context.
   *
   * @param context the job context written by {@link #enqueue}
   * @return the saved call
   */
  static AsyncRuleCall parse(JsonObject context) {
    return JsonUtil.fromJson(context.toString(), AsyncRuleCall.class);
  }

  /**
   * The sink for core's async rules: saves a call as a one-time job.
   *
   * @param call the saved write and rule
   */
  static void enqueue(AsyncRuleCall call) {
    enqueue(new Jobs(), call);
  }

  /** Saves a call with a given job manager. Visible for tests. */
  static void enqueue(Jobs jobs, AsyncRuleCall call) {
    Job job = new Job();
    job.name = "rule: " + call.ruleName;
    job.handlerType = Job.JAVA;
    job.handler = HANDLER;
    job.triggerType = "once";
    job.context = JsonUtil.fromJson(JsonUtil.toJson(call), JsonObject.class);
    job.maxRetries = PropertiesUtil.getInt("record.asyncRuleRetries", 3);
    job.retryDelaySeconds = PropertiesUtil.getInt("record.asyncRuleRetryDelaySeconds", 30);
    jobs.create(job);
  }
}
