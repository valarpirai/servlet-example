package com.example.config;

import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

/** jOOQ-backed repository for the app_properties table. */
public class PropertyRepository {

  private static final Table<Record> PROPS = table("app_properties");
  private static final Field<Long> ID = field("id", Long.class);
  private static final Field<String> NAME = field("name", String.class);
  private static final Field<String> VALUE = field("value", String.class);
  private static final Field<String> TYPE = field("type", String.class);
  private static final Field<String> DESCRIPTION = field("description", String.class);
  private static final Field<Boolean> ACTIVE = field("active", Boolean.class);
  private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
  private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
  private static final Field<String> CREATED_BY = field("created_by", String.class);
  private static final Field<String> UPDATED_BY = field("updated_by", String.class);

  private final Database database;

  public PropertyRepository(Database database) {
    this.database = database;
  }

  /** Fetch the value of a single active property by its dot-notation name. */
  public Optional<String> findValueByName(String name) throws Exception {
    return database.query(
        dsl ->
            dsl.select(VALUE)
                .from(PROPS)
                .where(NAME.eq(name).and(ACTIVE.isTrue()))
                .fetchOptional(VALUE));
  }

  /** Return all property rows ordered by name. */
  public List<AppProperty> findAll() throws Exception {
    return database.query(
        dsl ->
            dsl.select(
                    ID,
                    NAME,
                    VALUE,
                    TYPE,
                    DESCRIPTION,
                    ACTIVE,
                    CREATED_AT,
                    UPDATED_AT,
                    CREATED_BY,
                    UPDATED_BY)
                .from(PROPS)
                .orderBy(NAME)
                .fetch()
                .map(this::mapRecord));
  }

  /** Find a single row by primary key. */
  public Optional<AppProperty> findById(long id) throws Exception {
    return database.query(
        dsl ->
            dsl.select(
                    ID,
                    NAME,
                    VALUE,
                    TYPE,
                    DESCRIPTION,
                    ACTIVE,
                    CREATED_AT,
                    UPDATED_AT,
                    CREATED_BY,
                    UPDATED_BY)
                .from(PROPS)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::mapRecord));
  }

  /** Insert a new property row and return it with the generated id. */
  public AppProperty create(
      String name, String value, String type, String description, String actor) throws Exception {
    String resolvedType = type != null ? type : "STRING";
    String by = actor != null ? actor : "system";
    return database.query(
        dsl ->
            dsl.insertInto(PROPS)
                .columns(NAME, VALUE, TYPE, DESCRIPTION, CREATED_BY, UPDATED_BY)
                .values(name, value, resolvedType, description, by, by)
                .returning(
                    ID,
                    NAME,
                    VALUE,
                    TYPE,
                    DESCRIPTION,
                    ACTIVE,
                    CREATED_AT,
                    UPDATED_AT,
                    CREATED_BY,
                    UPDATED_BY)
                .fetchOne(this::mapRecord));
  }

  /** Update value and description of an existing property. */
  public Optional<AppProperty> update(long id, String value, String description, String actor)
      throws Exception {
    String by = actor != null ? actor : "system";
    return database.query(
        dsl ->
            dsl.update(PROPS)
                .set(VALUE, value)
                .set(DESCRIPTION, description)
                .set(UPDATED_BY, by)
                .set(UPDATED_AT, currentOffsetDateTime())
                .where(ID.eq(id))
                .returning(
                    ID,
                    NAME,
                    VALUE,
                    TYPE,
                    DESCRIPTION,
                    ACTIVE,
                    CREATED_AT,
                    UPDATED_AT,
                    CREATED_BY,
                    UPDATED_BY)
                .fetchOptional()
                .map(this::mapRecord));
  }

  /** Toggle the active flag of a property. */
  public boolean setActive(long id, boolean active, String actor) throws Exception {
    String by = actor != null ? actor : "system";
    return database.query(
        dsl ->
            dsl.update(PROPS)
                    .set(ACTIVE, active)
                    .set(UPDATED_BY, by)
                    .set(UPDATED_AT, currentOffsetDateTime())
                    .where(ID.eq(id))
                    .execute()
                > 0);
  }

  /** Hard-delete a property row. */
  public boolean delete(long id) throws Exception {
    return database.query(dsl -> dsl.delete(PROPS).where(ID.eq(id)).execute() > 0);
  }

  private AppProperty mapRecord(Record r) {
    OffsetDateTime createdAt = r.get(CREATED_AT);
    OffsetDateTime updatedAt = r.get(UPDATED_AT);
    return new AppProperty(
        r.get(ID),
        r.get(NAME),
        r.get(VALUE),
        r.get(TYPE),
        r.get(DESCRIPTION),
        Boolean.TRUE.equals(r.get(ACTIVE)),
        createdAt != null ? createdAt.toInstant() : null,
        updatedAt != null ? updatedAt.toInstant() : null,
        r.get(CREATED_BY),
        r.get(UPDATED_BY));
  }
}
