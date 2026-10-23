package com.example.config;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.util.Optional;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

/** jOOQ-backed lookup of a value in the app_properties table. */
public class PropertyRepository {

  private static final Table<Record> PROPS = table("app_properties");
  private static final Field<String> NAME = field("name", String.class);
  private static final Field<String> VALUE = field("value", String.class);
  private static final Field<Boolean> ACTIVE = field("active", Boolean.class);

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
}
