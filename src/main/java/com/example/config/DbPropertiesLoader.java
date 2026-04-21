package com.example.config;

import com.example.servlet.util.PropertiesUtil;

/**
 * One-time initialiser that wires a PropertyRepository into PropertiesUtil. Called early in
 * Main.main() after LoggingConfig so that all subsequent PropertiesUtil reads are DB-backed.
 */
public class DbPropertiesLoader {

  private static Database database;
  private static PropertyRepository repository;

  private DbPropertiesLoader() {}

  /** Connect to the DB and register the repository with PropertiesUtil. */
  public static void initialize() {
    DbConfig config = DbConfig.fromProperties();
    if (config == null) {
      System.err.println("[DbPropertiesLoader] db.url/username/password not configured");
      return;
    }
    try {
      config.getConnection().close(); // smoke-test
      database = new Database(config);
      repository = new PropertyRepository(database);
      PropertiesUtil.setPropertyRepository(repository);
      System.out.println("[DbPropertiesLoader] Connected to DB – property LRU cache active");
    } catch (Exception e) {
      System.err.println(
          "[DbPropertiesLoader] Cannot connect to DB, falling back to defaults: " + e.getMessage());
    }
  }

  public static Database getDatabase() {
    return database;
  }

  public static PropertyRepository getRepository() {
    return repository;
  }
}
