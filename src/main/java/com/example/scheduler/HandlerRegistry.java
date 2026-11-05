package com.example.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Maps a job's handler type and key to code. Type {@code script} runs the {@code api_scripts} row
 * of that name. Type {@code java} needs a handler registered with {@link #register}.
 */
public class HandlerRegistry {

  private final Map<String, JobHandler> handlers = new ConcurrentHashMap<>();
  private final Function<String, JobHandler> scriptHandlers;

  /**
   * Creates a registry with no Java handlers.
   *
   * @param scriptHandlers builds the handler for one script name
   */
  public HandlerRegistry(Function<String, JobHandler> scriptHandlers) {
    this.scriptHandlers = scriptHandlers;
  }

  /** Registers a Java handler under a key. */
  public void register(String key, JobHandler handler) {
    handlers.put(key, handler);
  }

  /**
   * Finds the code for a job.
   *
   * @param handlerType {@link Job#JAVA} or {@link Job#SCRIPT}
   * @param key value of the {@code handler} column
   * @return the handler, or null when the type is unknown or nothing is registered under the key
   */
  public JobHandler find(String handlerType, String key) {
    if (Job.SCRIPT.equals(handlerType)) {
      return scriptHandlers.apply(key);
    }
    return Job.JAVA.equals(handlerType) ? handlers.get(key) : null;
  }
}
