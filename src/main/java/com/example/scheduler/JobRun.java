package com.example.scheduler;

import java.time.Instant;

/** One row of {@code scheduled_job_runs}: what happened on a single run of a job. */
public class JobRun {

  public static final String SUCCESS = "success";
  public static final String FAILED = "failed";
  public static final String NO_HANDLER = "no_handler";
  public static final String PARKED = "parked";

  public String jobId;
  public String jobName;
  public String handlerType;
  public String handler;
  public String node;
  public String runId;
  public String status;
  public String error;
  public Instant startedAt;
  public Instant endedAt;
  public long durationMillis;
}
