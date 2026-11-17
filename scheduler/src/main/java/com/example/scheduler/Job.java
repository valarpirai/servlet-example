package com.example.scheduler;

import com.google.gson.JsonObject;
import java.time.Instant;

/**
 * One row of {@code scheduled_jobs}. Plain data; the repository owns every state change.
 *
 * <p>State values: {@link #READY}, {@link #QUEUED}, {@link #RUNNING}, {@link #ERROR}.
 */
public class Job {

  public static final int READY = 0;
  public static final int QUEUED = 1;
  public static final int RUNNING = 2;
  public static final int ERROR = 3;

  public String id;
  public String name;
  public static final String JAVA = "java";
  public static final String SCRIPT = "script";

  /** {@link #JAVA}: {@code handler} is a registered key. {@link #SCRIPT}: an api_scripts name. */
  public String handlerType = JAVA;

  public String handler;
  public String triggerType;
  public JsonObject triggerSpec = new JsonObject();
  public Instant nextRun;
  public int state = READY;
  public int priority = 100;
  public String owner;
  public String runId;
  public JsonObject context = new JsonObject();
  public String lastError;
  public Instant runEnd;

  /** Retries after a failed run. 0 means never retry. Defaults match the table defaults. */
  public int maxRetries = 0;

  /** Wait before the first retry. */
  public int retryDelaySeconds = 60;

  /** True doubles the wait on each further retry. False keeps it fixed. */
  public boolean retryExponential = true;

  /** Ceiling on any one wait, jitter included. */
  public int retryMaxDelaySeconds = 3600;

  /** Fraction 0 to 1. 0.2 spreads each wait by plus or minus 20 percent. */
  public double retryJitter = 0;

  /** Retries used so far. Reset after a success or when retries run out. */
  public int retryCount = 0;
}
