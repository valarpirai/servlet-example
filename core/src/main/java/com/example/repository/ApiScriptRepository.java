package com.example.repository;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.record.TableRecord;
import java.io.IOException;
import java.util.Optional;

/** Reads endpoint scripts from the {@code api_scripts} table. */
public class ApiScriptRepository {

  private static final String API_SCRIPTS = "api_scripts";

  private final Database database;

  /**
   * Creates a repository.
   *
   * @param database database holding api_scripts, or null to use the application database
   */
  public ApiScriptRepository(Database database) {
    this.database = database;
  }

  /**
   * Reads one script.
   *
   * @param name the api_scripts row name
   * @return the script text, or empty when no row has that name
   * @throws IOException when no database is available
   * @throws Exception when the query fails
   */
  public Optional<String> findContent(String name) throws Exception {
    Database db = database != null ? database : DbPropertiesLoader.getDatabase();
    if (db == null) {
      throw new IOException("API scripts are stored in the database, which is not ready");
    }
    TableRecord script = new TableRecord(db, API_SCRIPTS);
    script.addQuery("name", name);
    script.setLimit(1);
    script.query();
    return script.next() ? Optional.ofNullable(script.getValue("content")) : Optional.empty();
  }
}
