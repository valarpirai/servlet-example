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
}
