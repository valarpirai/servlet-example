package com.example.module;

import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.model.Module;
import com.example.util.PropertiesUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** CRUD for JavaScript modules, stored in the {@code modules} table. */
public class ModuleManager {

  private static final Logger logger = LoggerFactory.getLogger(ModuleManager.class);
  private static final Table<Record> MODULES = table("modules");
  private static final Field<String> NAME = field("name", String.class);
  private static final Field<String> CONTENT = field("content", String.class);
  private static final Field<Integer> SIZE_BYTES = field("size_bytes", Integer.class);
  private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
  private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);

  private static ModuleManager instance;
  private final Database database;
  private final long maxFileSize;

  /**
   * Creates a manager on a fixed database.
   *
   * @param database database to use, or null to look up the application database on first use
   */
  public ModuleManager(Database database) {
    this.database = database;
    this.maxFileSize = PropertiesUtil.getLong("modules.maxFileSize", 1048576L);
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

  public Module createModule(String modulePath, String content) throws IOException {
    validateModulePath(modulePath);
    validateContent(content);
    String name = normalizeName(modulePath);

    int inserted =
        run(
            dsl ->
                dsl.insertInto(MODULES)
                    .columns(NAME, CONTENT, SIZE_BYTES)
                    .values(name, content, content.getBytes().length)
                    .onConflictDoNothing()
                    .execute());
    if (inserted == 0) {
      throw new IOException("Module already exists: " + modulePath);
    }
    logger.info("Created module: {}", modulePath);
    return getModule(name);
  }

  public Module updateModule(String modulePath, String content) throws IOException {
    validateContent(content);
    String name = normalizeName(modulePath);

    int updated =
        run(
            dsl ->
                dsl.update(MODULES)
                    .set(CONTENT, content)
                    .set(SIZE_BYTES, content.getBytes().length)
                    .set(UPDATED_AT, currentOffsetDateTime())
                    .where(NAME.eq(name))
                    .execute());
    if (updated == 0) {
      throw new IOException("Module not found: " + modulePath);
    }
    logger.info("Updated module: {}", modulePath);
    return getModule(name);
  }

  public void deleteModule(String modulePath) throws IOException {
    String name = normalizeName(modulePath);

    int deleted = run(dsl -> dsl.deleteFrom(MODULES).where(NAME.eq(name)).execute());
    if (deleted == 0) {
      throw new IOException("Module not found: " + modulePath);
    }
    logger.info("Deleted module: {}", modulePath);
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
}
