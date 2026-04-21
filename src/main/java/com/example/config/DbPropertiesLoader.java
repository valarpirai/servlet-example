package com.example.config;

import com.example.servlet.util.PropertiesUtil;
import org.flywaydb.core.Flyway;

/**
 * One-time initialiser that:
 *
 * <ol>
 *   <li>Runs Flyway migrations (applies any pending {@code db/V*__*.sql} scripts)
 *   <li>Wires a {@link PropertyRepository} into {@link PropertiesUtil} for DB-backed LRU lookups
 * </ol>
 *
 * <p>Called early in {@code Main} static block, after {@code LoggingConfig}.
 */
public class DbPropertiesLoader {

  private static Database database;
  private static PropertyRepository repository;

  private DbPropertiesLoader() {}

  public static void initialize() {
    DbConfig config = DbConfig.fromProperties();
    if (config == null) {
      System.err.println("[DbPropertiesLoader] db.url/username/password not configured");
      return;
    }
    try {
      Flyway.configure()
          .dataSource(config.getUrl(), config.getUsername(), config.getPassword())
          .locations("classpath:db")
          .load()
          .migrate();

      database = new Database(config);
      repository = new PropertyRepository(database);
      PropertiesUtil.setPropertyRepository(repository);
      System.out.println("[DbPropertiesLoader] DB ready – schema migrated, LRU cache active");
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
