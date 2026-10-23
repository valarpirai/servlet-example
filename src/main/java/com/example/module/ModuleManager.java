package com.example.module;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.model.Module;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

/**
 * Reads JavaScript modules from the {@code js_modules} table for the {@code import} loader. The
 * module manager API writes them, as a script route over {@code TableRecord}.
 */
public class ModuleManager {

  private static final Table<Record> MODULES = table("js_modules");
  private static final Field<String> NAME = field("name", String.class);
  private static final Field<String> CONTENT = field("content", String.class);
  private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
  private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);

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
    return run(dsl -> dsl.select().from(MODULES).orderBy(NAME).fetch(ModuleManager::toModule));
  }

  public Module getModule(String modulePath) throws IOException {
    String name = normalizeName(modulePath);
    return run(
        dsl -> dsl.select().from(MODULES).where(NAME.eq(name)).fetchOne(ModuleManager::toModule));
  }

  private <T> T run(Database.Work<T> work) throws IOException {
    Database db = database != null ? database : DbPropertiesLoader.getDatabase();
    if (db == null) {
      throw new IOException("Modules are stored in the database, which is not ready");
    }
    try {
      return db.query(work);
    } catch (Exception e) {
      throw new IOException("Module storage failed: " + e.getMessage(), e);
    }
  }

  private static Module toModule(Record r) {
    String name = r.get(NAME);
    Module module = new Module(name, name + ".js", r.get(CONTENT));
    module.setCreatedAt(r.get(CREATED_AT).toInstant());
    module.setUpdatedAt(r.get(UPDATED_AT).toInstant());
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
