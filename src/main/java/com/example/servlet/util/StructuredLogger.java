package com.example.servlet.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.MDC;

/**
 * Utility for structured logging with correlation IDs and custom fields. Wraps SLF4J logger to
 * provide JSON-like structured logging.
 */
public class StructuredLogger {

  private final Logger logger;
  private static final Gson gson = new GsonBuilder().create();

  public StructuredLogger(Logger logger) {
    this.logger = logger;
  }

  /**
   * Create a structured logger from an SLF4J logger.
   *
   * @param logger SLF4J logger
   * @return Structured logger instance
   */
  public static StructuredLogger create(Logger logger) {
    return new StructuredLogger(logger);
  }

  /**
   * Log info message with structured fields.
   *
   * @param message Log message
   * @param fields Additional structured fields
   */
  public void info(String message, Map<String, Object> fields) {
    if (logger.isInfoEnabled()) {
      logger.info(formatStructured(message, fields));
    }
  }

  /**
   * Log info message without additional fields.
   *
   * @param message Log message
   */
  public void info(String message) {
    info(message, null);
  }

  /**
   * Log warning message with structured fields.
   *
   * @param message Log message
   * @param fields Additional structured fields
   */
  public void warn(String message, Map<String, Object> fields) {
    if (logger.isWarnEnabled()) {
      logger.warn(formatStructured(message, fields));
    }
  }

  /**
   * Log warning message without additional fields.
   *
   * @param message Log message
   */
  public void warn(String message) {
    warn(message, null);
  }

  /**
   * Log error message with structured fields.
   *
   * @param message Log message
   * @param fields Additional structured fields
   */
  public void error(String message, Map<String, Object> fields) {
    logger.error(formatStructured(message, fields));
  }

  /**
   * Log error message with exception and structured fields.
   *
   * @param message Log message
   * @param throwable Exception
   * @param fields Additional structured fields
   */
  public void error(String message, Throwable throwable, Map<String, Object> fields) {
    logger.error(formatStructured(message, fields), throwable);
  }

  /**
   * Log error message with exception.
   *
   * @param message Log message
   * @param throwable Exception
   */
  public void error(String message, Throwable throwable) {
    error(message, throwable, null);
  }

  /**
   * Log debug message with structured fields.
   *
   * @param message Log message
   * @param fields Additional structured fields
   */
  public void debug(String message, Map<String, Object> fields) {
    if (logger.isDebugEnabled()) {
      logger.debug(formatStructured(message, fields));
    }
  }

  /**
   * Log debug message without additional fields.
   *
   * @param message Log message
   */
  public void debug(String message) {
    debug(message, null);
  }

  /**
   * Format message with structured fields including MDC context.
   *
   * @param message Log message
   * @param additionalFields Additional fields to include
   * @return Formatted message with JSON-like structure
   */
  private String formatStructured(String message, Map<String, Object> additionalFields) {
    Map<String, Object> logData = new HashMap<>();
    logData.put("message", message);

    // Add MDC context (correlation ID, request ID, etc.)
    Map<String, String> mdcContext = MDC.getCopyOfContextMap();
    if (mdcContext != null && !mdcContext.isEmpty()) {
      logData.putAll(mdcContext);
    }

    // Add additional fields
    if (additionalFields != null && !additionalFields.isEmpty()) {
      logData.putAll(additionalFields);
    }

    // Return formatted string (JSON-like but readable)
    return formatLogData(logData);
  }

  /**
   * Format log data as readable structured string.
   *
   * @param logData Log data map
   * @return Formatted string
   */
  private String formatLogData(Map<String, Object> logData) {
    // Extract message first
    String message = (String) logData.get("message");
    logData.remove("message");

    if (logData.isEmpty()) {
      return message;
    }

    // Format as: "message | field1=value1 field2=value2"
    StringBuilder sb = new StringBuilder();
    sb.append(message);

    if (!logData.isEmpty()) {
      sb.append(" | ");
      boolean first = true;
      for (Map.Entry<String, Object> entry : logData.entrySet()) {
        if (!first) {
          sb.append(" ");
        }
        sb.append(entry.getKey()).append("=");

        Object value = entry.getValue();
        if (value instanceof String) {
          sb.append(value);
        } else {
          sb.append(gson.toJson(value));
        }
        first = false;
      }
    }

    return sb.toString();
  }

  /**
   * Create a builder for adding fields incrementally.
   *
   * @param message Log message
   * @return Builder instance
   */
  public LogBuilder with(String message) {
    return new LogBuilder(this, message);
  }

  /** Builder for constructing structured log messages. */
  public static class LogBuilder {
    private final StructuredLogger logger;
    private final String message;
    private final Map<String, Object> fields = new HashMap<>();

    private LogBuilder(StructuredLogger logger, String message) {
      this.logger = logger;
      this.message = message;
    }

    /**
     * Add a field to the log entry.
     *
     * @param key Field name
     * @param value Field value
     * @return Builder instance
     */
    public LogBuilder field(String key, Object value) {
      fields.put(key, value);
      return this;
    }

    /** Log at info level. */
    public void info() {
      logger.info(message, fields);
    }

    /** Log at warning level. */
    public void warn() {
      logger.warn(message, fields);
    }

    /** Log at error level. */
    public void error() {
      logger.error(message, fields);
    }

    /**
     * Log at error level with exception.
     *
     * @param throwable Exception
     */
    public void error(Throwable throwable) {
      logger.error(message, throwable, fields);
    }

    /** Log at debug level. */
    public void debug() {
      logger.debug(message, fields);
    }
  }
}
