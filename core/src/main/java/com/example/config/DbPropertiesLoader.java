package com.example.config;

import com.example.repository.PropertyRepository;
import com.example.util.PropertiesUtil;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final Logger logger = LoggerFactory.getLogger(DbPropertiesLoader.class);

  private static DbConfig dbConfig;
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
      dbConfig = config;
      repository = new PropertyRepository(database);
      PropertiesUtil.setPropertySource(repository);
      System.out.println("[DbPropertiesLoader] DB ready – schema migrated, LRU cache active");
    } catch (Exception e) {
      throw new IllegalStateException("Cannot connect to the database: " + e.getMessage(), e);
    }
  }

  /** Close the connection pool. Main calls this after Tomcat stops, so no request loses its DB. */
  public static void close() {
    if (dbConfig != null) {
      dbConfig.close();
    }
  }

  public static Database getDatabase() {
    return database;
  }

  /**
   * Checks that the application database answers a trivial query.
   *
   * @return false when no database is configured or the query fails; the failure is logged
   */
  public static boolean isDatabaseUp() {
    if (database == null) {
      return false;
    }
    try {
      database.query(dsl -> dsl.selectOne().fetch());
      return true;
    } catch (Exception e) {
      logger.warn("Health check failed: {}", e.getMessage());
      return false;
    }
  }

  public static PropertyRepository getRepository() {
    return repository;
  }
}
