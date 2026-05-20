package com.example.servlet.exception;

/** Thrown when JavaScript execution fails due to a runtime error or security violation. */
public class ScriptExecutionException extends RuntimeException {

  public ScriptExecutionException(String message) {
    super(message);
  }

  public ScriptExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
