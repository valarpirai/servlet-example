package com.example.scheduler;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.util.HashMap;
import java.util.Map;

/**
 * Finds {@link JobName} classes with ClassGraph and registers them in a {@link HandlerRegistry}.
 */
public final class JobHandlerScanner {

  private JobHandlerScanner() {}

  /**
   * Scans a package tree and registers every {@link JobName} handler.
   *
   * @param registry where the handlers go
   * @param packageName root package to scan, for example {@code com.example}
   * @return the registered names, mapped to their classes
   * @throws IllegalStateException when a class is not a {@link JobHandler}, lacks a public
   *     no-argument constructor, or shares a name with another class
   */
  public static Map<String, Class<?>> scanAndRegister(
      HandlerRegistry registry, String packageName) {
    Map<String, Class<?>> found = new HashMap<>();
    try (ScanResult scan =
        new ClassGraph().acceptPackages(packageName).enableAnnotationInfo().scan()) {
      for (ClassInfo info : scan.getClassesWithAnnotation(JobName.class)) {
        Class<?> type = info.loadClass();
        String name = type.getAnnotation(JobName.class).value();
        if (name.isBlank()) {
          throw new IllegalStateException("@JobName on " + type.getName() + " has a blank name");
        }
        if (!JobHandler.class.isAssignableFrom(type)) {
          throw new IllegalStateException(
              "@JobName class " + type.getName() + " must implement JobHandler");
        }
        Class<?> other = found.put(name, type);
        if (other != null) {
          throw new IllegalStateException(
              "Job name '" + name + "' is used by " + other.getName() + " and " + type.getName());
        }
        registry.register(name, instantiate(type));
      }
    }
    return found;
  }

  private static JobHandler instantiate(Class<?> type) {
    try {
      return (JobHandler) type.getConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "@JobName class " + type.getName() + " needs a public no-argument constructor", e);
    }
  }
}
