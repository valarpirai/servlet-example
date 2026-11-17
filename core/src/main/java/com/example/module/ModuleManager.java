package com.example.module;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.model.Module;
import com.example.record.TableRecord;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads JavaScript modules from the {@code js_modules} table for the {@code import} loader. The
 * module manager API writes them, as a script route over {@code TableRecord}.
 */
public class ModuleManager {

  private static final String MODULES = "js_modules";

  private static ModuleManager instance;
  private final Database database;

  /**
   * Creates a manager on a fixed database.
   *
   * @param database database to use, or null to look up the application database on first use
   */
  public ModuleManager(Database database) {
    this.database = database;
  }

  public static synchronized ModuleManager getInstance() {
    if (instance == null) {
      instance = new ModuleManager(null);
    }
    return instance;
  }

  public List<Module> listModules() throws IOException {
    return read(null);
  }

  public Module getModule(String modulePath) throws IOException {
    List<Module> found = read(normalizeName(modulePath));
    return found.isEmpty() ? null : found.get(0);
  }

  /** Reads modules ordered by name; one named module when {@code name} is set. */
  private List<Module> read(String name) throws IOException {
    Database db = database != null ? database : DbPropertiesLoader.getDatabase();
    if (db == null) {
      throw new IOException("Modules are stored in the database, which is not ready");
    }
    try {
      TableRecord modules = new TableRecord(db, MODULES);
      if (name != null) {
        modules.addQuery("name", name);
      }
      modules.orderBy("name");
      modules.query();
      List<Module> result = new ArrayList<>();
      while (modules.next()) {
        result.add(toModule(modules));
      }
      return result;
    } catch (RuntimeException e) {
      throw new IOException("Module storage failed: " + e.getMessage(), e);
    }
  }

  private static Module toModule(TableRecord r) {
    String name = r.getValue("name");
    Module module = new Module(name, name + ".js", r.getValue("content"));
    module.setCreatedAt(OffsetDateTime.parse(r.getValue("created_at")).toInstant());
    module.setUpdatedAt(OffsetDateTime.parse(r.getValue("updated_at")).toInstant());
    return module;
  }

  /** Strips slashes and the {@code .js} suffix, so {@code /utils/math.js} becomes utils/math. */
  private String normalizeName(String modulePath) {
    String name = modulePath.replace("\\", "/").replaceAll("^/+|/+$", "");
    if (name.endsWith(".js")) {
      name = name.substring(0, name.length() - 3);
    }
    if (name.contains("..")) {
      throw new SecurityException("Invalid module path: " + modulePath);
    }
    return name;
  }
}
