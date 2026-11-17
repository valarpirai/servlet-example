package com.example.repository;

import com.example.config.Database;
import com.example.record.TableRecord;
import com.example.util.PropertySource;
import java.util.Optional;

/** Lookup of a value in the app_properties table, read through {@link TableRecord}. */
public class PropertyRepository implements PropertySource {

  private final Database database;

  public PropertyRepository(Database database) {
    this.database = database;
  }

  /**
   * Fetches the value of a single active property by its dot-notation name.
   *
   * @param name property name
   * @return the value, or empty when no active row has the name or its value is null
   */
  @Override
  public Optional<String> findValueByName(String name) throws Exception {
    TableRecord properties = new TableRecord(database, "app_properties");
    properties.addQuery("name", name);
    properties.addQuery("active", true);
    properties.setLimit(1);
    properties.query();
    return properties.next() ? Optional.ofNullable(properties.getValue("value")) : Optional.empty();
  }
}
