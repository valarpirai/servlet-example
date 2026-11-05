package com.example.record;

import com.example.util.PropertiesUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Optional restriction on the tables {@link TableRecord} may touch. The setting is a comma
 * separated list of {@code table} or {@code table:keyColumn} entries. A blank setting permits every
 * table. It is read from the config file only, never from the database.
 */
public final class AllowedTables {

  private static volatile AllowedTables current;

  private final boolean all;
  private final Map<String, String> keys;

  private AllowedTables(boolean all, Map<String, String> keys) {
    this.all = all;
    this.keys = keys;
  }

  /**
   * Parses a setting value.
   *
   * @param setting the raw value, or null
   * @return the parsed restriction; every table is permitted when the value is blank
   */
  public static AllowedTables parse(String setting) {
    if (setting == null || setting.isBlank()) {
      return new AllowedTables(true, Map.of());
    }
    Map<String, String> keys = new HashMap<>();
    for (String entry : setting.split(",")) {
      String trimmed = entry.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int colon = trimmed.indexOf(':');
      String table = (colon < 0 ? trimmed : trimmed.substring(0, colon)).trim();
      String key = colon < 0 ? "" : trimmed.substring(colon + 1).trim();
      if (!table.isEmpty()) {
        keys.put(table, key);
      }
    }
    return new AllowedTables(false, keys);
  }

  /**
   * Reads the setting from the config file, once.
   *
   * @return the restriction in force
   */
  public static AllowedTables current() {
    AllowedTables value = current;
    if (value == null) {
      value = parse(PropertiesUtil.getFileString("record.allowedTables", ""));
      current = value;
    }
    return value;
  }

  /**
   * Replaces the cached setting. Tests call this; {@link #reset()} undoes it.
   *
   * @param value the restriction to use
   */
  public static void override(AllowedTables value) {
    current = value;
  }

  /** Forgets the cached setting. Tests call this. */
  public static void reset() {
    current = null;
  }

  /**
   * Tells whether a table may be used.
   *
   * @param table table name
   * @return true when the table is permitted
   */
  public boolean permits(String table) {
    return all || keys.containsKey(table);
  }

  /**
   * Returns the key column the setting names for a table.
   *
   * @param table table name
   * @return the listed key column, or empty when none is listed
   */
  public Optional<String> keyFor(String table) {
    String key = keys.get(table);
    return key == null || key.isEmpty() ? Optional.empty() : Optional.of(key);
  }
}
