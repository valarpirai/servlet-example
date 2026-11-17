package com.example.scheduler;

import lombok.Data;

/**
 * A job as scripts read it: plain text and numbers. JSON fields arrive as JSON text, times as ISO
 * text.
 */
@Data
public class JobInfo {

  private String id;
  private String name;
  private String handlerType;
  private String handler;
  private String triggerType;
  private String triggerSpec;
  private String nextRun;

  /** Numeric state: 0 ready, 1 queued, 2 running, 3 error. */
  private int state;

  private String stateName;
  private int priority;
  private String owner;
  private String context;
  private String lastError;
  private String runEnd;
  private int maxRetries;
  private int retryDelaySeconds;
  private boolean retryExponential;
  private int retryMaxDelaySeconds;
  private double retryJitter;
  private int retryCount;

  /**
   * Copies a job into its script view.
   *
   * @param job the job
   * @return the view
   */
  static JobInfo of(Job job) {
    JobInfo info = new JobInfo();
    info.id = job.id;
    info.name = job.name;
    info.handlerType = job.handlerType;
    info.handler = job.handler;
    info.triggerType = job.triggerType;
    info.triggerSpec = String.valueOf(job.triggerSpec);
    info.nextRun = job.nextRun == null ? null : job.nextRun.toString();
    info.state = job.state;
    info.stateName = stateName(job.state);
    info.priority = job.priority;
    info.owner = job.owner;
    info.context = String.valueOf(job.context);
    info.lastError = job.lastError;
    info.runEnd = job.runEnd == null ? null : job.runEnd.toString();
    info.maxRetries = job.maxRetries;
    info.retryDelaySeconds = job.retryDelaySeconds;
    info.retryExponential = job.retryExponential;
    info.retryMaxDelaySeconds = job.retryMaxDelaySeconds;
    info.retryJitter = job.retryJitter;
    info.retryCount = job.retryCount;
    return info;
  }

  private static String stateName(int state) {
    return switch (state) {
      case Job.READY -> "ready";
      case Job.QUEUED -> "queued";
      case Job.RUNNING -> "running";
      case Job.ERROR -> "error";
      default -> "unknown";
    };
  }
}
