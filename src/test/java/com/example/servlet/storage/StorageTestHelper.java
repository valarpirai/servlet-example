package com.example.servlet.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Helper class for storage-related tests to reduce code duplication. */
public class StorageTestHelper {

  /**
   * Cleans up the attachments directory before tests to ensure clean state.
   *
   * @throws IOException if cleanup fails
   */
  public static void cleanupAttachmentsDirectory() throws IOException {
    Path storageDir = Path.of("attachments");
    if (Files.exists(storageDir)) {
      Files.walk(storageDir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException e) {
                  // Ignore cleanup errors
                }
              });
    }
  }

  /**
   * Resets singleton instance using reflection to ensure clean state between tests.
   *
   * @param singletonClass the singleton class to reset
   * @throws Exception if reflection fails
   */
  public static void resetSingleton(Class<?> singletonClass) throws Exception {
    java.lang.reflect.Field instanceField = singletonClass.getDeclaredField("instance");
    instanceField.setAccessible(true);
    instanceField.set(null, null);
  }
}
