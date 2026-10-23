package com.example.config;

import com.example.util.PropertiesUtil;
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
      throw new IllegalStateException("db.url, db.username and db.password must be configured");
    }
    try {
      Flyway.configure()
          .dataSource(config.getUrl(), config.getUsername(), config.getPassword())
          .locations("classpath:db")
          .load()
          .migrate();

      database = new Database(config);
      Runtime.getRuntime().addShutdownHook(new Thread(config::close));
      repository = new PropertyRepository(database);
      PropertiesUtil.setPropertyRepository(repository);
      System.out.println("[DbPropertiesLoader] DB ready – schema migrated, LRU cache active");
    } catch (Exception e) {
      throw new IllegalStateException("Cannot connect to the database: " + e.getMessage(), e);
    }
  }

  public static Database getDatabase() {
    return database;
  }

  public static PropertyRepository getRepository() {
    return repository;
  }
}
