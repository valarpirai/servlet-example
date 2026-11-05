package com.example.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Maps a job's {@code handler} text to code. A key {@code script:<name>} runs the {@code
 * api_scripts} row of that name. Any other key must be registered with {@link #register}.
 */
public class HandlerRegistry {

  private static final String SCRIPT_PREFIX = "script:";

  private final Map<String, JobHandler> handlers = new ConcurrentHashMap<>();
  private final Function<String, JobHandler> scriptHandlers;

  /**
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
   * Finds the code for a handler key.
   *
   * @param key value of the {@code handler} column
   * @return the handler, or null when nothing is registered under the key
   */
  public JobHandler find(String key) {
    if (key.startsWith(SCRIPT_PREFIX)) {
      return scriptHandlers.apply(key.substring(SCRIPT_PREFIX.length()));
    }
    return handlers.get(key);
  }
}
