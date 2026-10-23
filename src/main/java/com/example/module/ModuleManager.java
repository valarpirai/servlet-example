package com.example.module;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.model.Module;
import com.example.record.TableRecord;
import com.example.util.PropertiesUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CRUD for JavaScript modules, stored in the {@code js_modules} table. Reads use jOOQ. Writes go
 * through {@link TableRecord}, so business rules on {@code js_modules} apply.
 */
public class ModuleManager {

  /** A before business rule stopped the write. */
  public static class BlockedException extends IOException {
    public BlockedException(String message) {
      super(message);
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(ModuleManager.class);
  private static final Table<Record> MODULES = table("js_modules");
  private static final Field<String> NAME = field("name", String.class);
  private static final Field<String> CONTENT = field("content", String.class);
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

  /**
   * Creates a module. The write goes through {@link TableRecord}, so business rules on {@code
   * modules} run.
   *
   * @param modulePath module name, with or without the {@code .js} suffix
   * @param content module source
   * @return the stored module
   * @throws IOException when the name or content is invalid, the module exists, or a rule stops it
   */
  public Module createModule(String modulePath, String content) throws IOException {
    validateModulePath(modulePath);
    validateContent(content);
    String name = normalizeName(modulePath);
    if (getModule(name) != null) {
      throw new IOException("Module already exists: " + modulePath);
    }

    TableRecord record = record();
    record.setValue("name", name);
    record.setValue("content", content);
    record.setValue("size_bytes", content.getBytes().length);
    if (!write(() -> record.insert() != null)) {
      throw new BlockedException("A business rule stopped the create of module: " + modulePath);
    }
    logger.info("Created module: {}", modulePath);
    return getModule(name);
  }

  /**
   * Replaces a module's content through {@link TableRecord}, so business rules run.
   *
   * @param modulePath module name
   * @param content new module source
   * @return the stored module
   * @throws IOException when the module is missing, the content is invalid, or a rule stops it
   */
  public Module updateModule(String modulePath, String content) throws IOException {
    validateContent(content);
    String name = normalizeName(modulePath);

    TableRecord record = find(name, modulePath);
    record.setValue("content", content);
    record.setValue("size_bytes", content.getBytes().length);
    record.setValue("updated_at", OffsetDateTime.now());
    if (!write(record::update)) {
      throw new BlockedException("A business rule stopped the update of module: " + modulePath);
    }
    logger.info("Updated module: {}", modulePath);
    return getModule(name);
  }

  /**
   * Deletes a module through {@link TableRecord}, so business rules run.
   *
   * @param modulePath module name
   * @throws IOException when the module is missing or a rule stops the delete
   */
  public void deleteModule(String modulePath) throws IOException {
    String name = normalizeName(modulePath);

    TableRecord record = find(name, modulePath);
    if (!write(record::deleteRecord)) {
      throw new BlockedException("A business rule stopped the delete of module: " + modulePath);
    }
    logger.info("Deleted module: {}", modulePath);
  }

  /** Loads the row of a module, or fails when there is none. */
  private TableRecord find(String name, String modulePath) throws IOException {
    TableRecord record = record();
    record.addQuery("name", name);
    try {
      record.query();
    } catch (RuntimeException e) {
      throw new IOException("Module storage failed: " + e.getMessage(), e);
    }
    if (!record.next()) {
      throw new IOException("Module not found: " + modulePath);
    }
    return record;
  }

  private TableRecord record() throws IOException {
    return new TableRecord(db(), "js_modules");
  }

  /** Runs a write and turns its failures into IOException. */
  private boolean write(java.util.function.BooleanSupplier write) throws IOException {
    try {
      return write.getAsBoolean();
    } catch (RuntimeException e) {
      throw new IOException("Module storage failed: " + e.getMessage(), e);
    }
  }

  private Database db() throws IOException {
    Database db = database != null ? database : DbPropertiesLoader.getDatabase();
    if (db == null) {
      throw new IOException("Modules are stored in the database, which is not ready");
    }
    return db;
  }

  private <T> T run(Database.Work<T> work) throws IOException {
    try {
      return db().query(work);
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
