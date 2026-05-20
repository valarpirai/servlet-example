package com.example.servlet.exception;

import java.io.IOException;

/** Thrown when attachment storage or retrieval fails. Wraps the underlying I/O cause. */
public class StorageException extends IOException {

  public StorageException(String message) {
    super(message);
  }

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
