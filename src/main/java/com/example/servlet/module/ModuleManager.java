package com.example.servlet.module;

import com.example.servlet.model.Module;
import com.example.servlet.util.PropertiesUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModuleManager {

  private static final Logger logger = LoggerFactory.getLogger(ModuleManager.class);
  private static ModuleManager instance;
  private final Path modulesDirectory;
  private final long maxFileSize;

  private ModuleManager() {
    String modulesDir = PropertiesUtil.getString("modules.directory", "modules");
    this.modulesDirectory = Paths.get(modulesDir);
    this.maxFileSize = PropertiesUtil.getLong("modules.maxFileSize", 1048576L);

    // Create modules directory if it doesn't exist
    try {
      if (!Files.exists(modulesDirectory)) {
        Files.createDirectories(modulesDirectory);
        logger.info("Created modules directory at: {}", modulesDirectory.toAbsolutePath());
      }
    } catch (IOException e) {
      logger.error("Failed to create modules directory", e);
      throw new RuntimeException("Failed to initialize module system", e);
    }
  }

  public static synchronized ModuleManager getInstance() {
    if (instance == null) {
      instance = new ModuleManager();
    }
    return instance;
  }

  public List<Module> listModules() throws IOException {
    List<Module> modules = new ArrayList<>();

    try (Stream<Path> paths = Files.walk(modulesDirectory)) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".js"))
          .forEach(
              path -> {
                try {
                  modules.add(loadModuleFromFile(path));
                } catch (IOException e) {
                  logger.error("Failed to load module: {}", path, e);
                }
              });
    }

    return modules;
  }

  public Module getModule(String modulePath) throws IOException {
    Path filePath = resolveModulePath(modulePath);

    if (!Files.exists(filePath)) {
      return null;
    }

    return loadModuleFromFile(filePath);
  }

  public Module createModule(String modulePath, String content) throws IOException {
    validateModulePath(modulePath);
    validateContent(content);

    Path filePath = resolveModulePath(modulePath);

    // Check if module already exists
    if (Files.exists(filePath)) {
      throw new IOException("Module already exists: " + modulePath);
    }

    // Create parent directories if needed
    Path parent = filePath.getParent();
    if (parent != null && !Files.exists(parent)) {
      Files.createDirectories(parent);
    }

    // Write content
    Files.writeString(filePath, content);
    logger.info("Created module: {}", modulePath);

    return loadModuleFromFile(filePath);
  }

  public Module updateModule(String modulePath, String content) throws IOException {
    validateContent(content);

    Path filePath = resolveModulePath(modulePath);

    if (!Files.exists(filePath)) {
      throw new IOException("Module not found: " + modulePath);
    }

    Files.writeString(filePath, content);
    logger.info("Updated module: {}", modulePath);

    return loadModuleFromFile(filePath);
  }

  public void deleteModule(String modulePath) throws IOException {
    Path filePath = resolveModulePath(modulePath);

    if (!Files.exists(filePath)) {
      throw new IOException("Module not found: " + modulePath);
    }

    Files.delete(filePath);
    logger.info("Deleted module: {}", modulePath);

    // Clean up empty parent directories
    cleanupEmptyDirectories(filePath.getParent());
  }

  private Module loadModuleFromFile(Path filePath) throws IOException {
    String content = Files.readString(filePath);
    String relativePath = modulesDirectory.relativize(filePath).toString();
    String moduleName = relativePath.replace(".js", "").replace("\\", "/");

    BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

    Module module = new Module(moduleName, relativePath, content);
    module.setCreatedAt(attrs.creationTime().toInstant());
    module.setUpdatedAt(attrs.lastModifiedTime().toInstant());

    return module;
  }

  private Path resolveModulePath(String modulePath) {
    // Normalize path separators
    String normalizedPath = modulePath.replace("\\", "/");

    // Remove leading/trailing slashes
    normalizedPath = normalizedPath.replaceAll("^/+|/+$", "");

    // Ensure .js extension
    if (!normalizedPath.endsWith(".js")) {
      normalizedPath += ".js";
    }

    Path resolved = modulesDirectory.resolve(normalizedPath).normalize();

    // Security check: ensure resolved path is within modules directory
    if (!resolved.startsWith(modulesDirectory)) {
      throw new SecurityException("Invalid module path: " + modulePath);
    }

    return resolved;
  }

  private void validateModulePath(String modulePath) throws IOException {
    if (modulePath == null || modulePath.trim().isEmpty()) {
      throw new IOException("Module path cannot be empty");
    }

    // Check for invalid characters
    if (modulePath.matches(".*[<>:\"|?*].*")) {
      throw new IOException("Module path contains invalid characters");
    }

    // Check for path traversal attempts
    if (modulePath.contains("..")) {
      throw new IOException("Module path cannot contain '..'");
    }
  }

  private void validateContent(String content) throws IOException {
    if (content == null) {
      throw new IOException("Module content cannot be null");
    }

    if (content.getBytes().length > maxFileSize) {
      throw new IOException("Module content exceeds maximum size of " + maxFileSize + " bytes");
    }
  }

  private void cleanupEmptyDirectories(Path directory) {
    if (directory == null
        || !directory.startsWith(modulesDirectory)
        || directory.equals(modulesDirectory)) {
      return;
    }

    try {
      if (Files.isDirectory(directory) && isDirectoryEmpty(directory)) {
        Files.delete(directory);
        logger.debug("Deleted empty directory: {}", directory);
        cleanupEmptyDirectories(directory.getParent());
      }
    } catch (IOException e) {
      logger.debug("Could not delete directory: {}", directory, e);
    }
  }

  private boolean isDirectoryEmpty(Path directory) throws IOException {
    try (Stream<Path> entries = Files.list(directory)) {
      return entries.findFirst().isEmpty();
    }
  }

  public Path getModulesDirectory() {
    return modulesDirectory;
  }
}
