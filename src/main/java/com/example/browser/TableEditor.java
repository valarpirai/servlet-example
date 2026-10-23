package com.example.browser;

import com.example.config.Database;
import com.example.record.TableRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates, updates and deletes rows for the table browser. Every write goes through {@link
 * TableRecord}, so the allow-list and the before and after business rules apply. Form values arrive
 * as text and are turned into the type of their column first.
 */
public class TableEditor {

  /** Column types a form can write. Others, such as json or uuid, are refused. */
  private static final Set<String> TEXT_TYPES = Set.of("text", "character varying", "character");

  /** Raised when the table is not on the {@code record.allowedTables} list. */
  public static class NotWritableException extends RuntimeException {
    public NotWritableException(String table) {
      super("Table is read-only: " + table);
    }
  }

  /** Raised when a before business rule stopped the write. */
  public static class BlockedException extends RuntimeException {
    public BlockedException(String message) {
      super(message);
    }
  }

  private final Database database;
  private final TableBrowser browser;

  public TableEditor(Database database) {
    this.database = database;
    this.browser = new TableBrowser(database);
  }

  /**
   * Inserts a row. A field left null or blank is left out, so the column default applies.
   *
   * @param table table to write
   * @param values column name to form value
   * @return the key of the new row
   * @throws NotWritableException when the table is not allow-listed
   * @throws BlockedException when a before rule aborts the insert
   * @throws IllegalArgumentException when a column or value is not valid
   */
  public String create(String table, Map<String, Object> values) throws Exception {
    Map<String, String> types = types(table);
    TableRecord record = new TableRecord(database, table);
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      Object typed = coerce(entry.getKey(), types, entry.getValue());
      if (typed != null) {
        record.setValue(entry.getKey(), typed);
      }
    }
    String key = record.insert();
    if (key == null) {
      throw new BlockedException("A business rule stopped the insert");
    }
    return key;
  }

  /**
   * Updates the fields of one row.
   *
   * @param table table to write
   * @param id key of the row, as text
   * @param values column name to new value. Send only the fields that changed
   * @return true when updated, false when the row does not exist
   * @throws BlockedException when a before rule aborts the update
   */
  public boolean update(String table, String id, Map<String, Object> values) throws Exception {
    Map<String, String> types = types(table);
    String keyColumn = TableRecord.keyColumnOf(table);
    TableRecord record = find(table, keyColumn, types, id);
    if (record == null) {
      return false;
    }
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      if (entry.getKey().equals(keyColumn)) {
        throw new IllegalArgumentException("The key cannot be changed: " + keyColumn);
      }
      record.setValue(entry.getKey(), coerce(entry.getKey(), types, entry.getValue()));
    }
    if (!record.update()) {
      throw new BlockedException("A business rule stopped the update");
    }
    return true;
  }

  /**
   * Deletes rows one by one. A row a rule blocks, or that is missing, does not stop the others.
   *
   * @param table table to write
   * @param ids keys of the rows, as text
   * @return {@code deleted} (count) and {@code failed} (one map of {@code id} and {@code reason}
   *     per row that was not deleted)
   */
  public Map<String, Object> delete(String table, List<String> ids) throws Exception {
    Map<String, String> types = types(table);
    String keyColumn = TableRecord.keyColumnOf(table);
    int deleted = 0;
    List<Map<String, String>> failed = new ArrayList<>();
    for (String id : ids) {
      String reason = null;
      try {
        TableRecord record = find(table, keyColumn, types, id);
        if (record == null) {
          reason = "Row not found";
        } else if (record.deleteRecord()) {
          deleted++;
        } else {
          reason = "A business rule stopped the delete";
        }
      } catch (IllegalArgumentException | IllegalStateException e) {
        reason = e.getMessage();
      }
      if (reason != null) {
        failed.add(Map.of("id", id, "reason", reason));
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("deleted", deleted);
    out.put("failed", failed);
    return out;
  }

  /** Column name to data type for a writable table. */
  private Map<String, String> types(String table) throws Exception {
    if (!browser.exists(table)) {
      throw new IllegalArgumentException("Table not found: " + table);
    }
    if (!TableBrowser.writable(table, browser.primaryKey(table))) {
      throw new NotWritableException(table);
    }
    Map<String, String> types = new LinkedHashMap<>();
    browser.columns(table).forEach(c -> types.put((String) c.get("name"), (String) c.get("type")));
    return types;
  }

  /** Loads the row with this key into a record, or returns null when there is none. */
  private TableRecord find(String table, String keyColumn, Map<String, String> types, String id) {
    TableRecord record = new TableRecord(database, table);
    record.addQuery(keyColumn, coerce(keyColumn, types, id));
    record.query();
    return record.next() ? record : null;
  }

  /**
   * Turns a form value into the Java type of its column.
   *
   * @param column column name
   * @param types column name to data type
   * @param raw value from the form: text, a number, a boolean or null
   * @return the typed value, or null for null and for blank text in a non-text column
   * @throws IllegalArgumentException when the column is unknown, not writable, or the value does
   *     not fit the type
   */
  static Object coerce(String column, Map<String, String> types, Object raw) {
    String type = types.get(column);
    if (type == null) {
      throw new IllegalArgumentException("Unknown column: " + column);
    }
    if (raw == null) {
      return null;
    }
    String text = asText(raw);
    if (TEXT_TYPES.contains(type)) {
      return text;
    }
    if (text.isBlank()) {
      return null;
    }
    try {
      return switch (type) {
        case "smallint", "integer", "bigint" -> Long.parseLong(text.trim());
        case "numeric" -> new BigDecimal(text.trim());
        case "real", "double precision" -> Double.parseDouble(text.trim());
        case "boolean" -> bool(text.trim());
        case "timestamp with time zone" -> OffsetDateTime.parse(text.trim());
        case "timestamp without time zone" -> LocalDateTime.parse(text.trim().replace(' ', 'T'));
        case "date" -> LocalDate.parse(text.trim());
        default ->
            throw new IllegalArgumentException(
                "Column " + column + " has a type that cannot be edited here: " + type);
      };
    } catch (NumberFormatException | DateTimeParseException e) {
      throw new IllegalArgumentException(
          "Invalid value for " + column + " (" + type + "): " + text, e);
    }
  }

  private static String asText(Object raw) {
    if (raw instanceof Double d && d == Math.rint(d) && !Double.isInfinite(d)) {
      return String.valueOf(d.longValue());
    }
    return String.valueOf(raw);
  }

  private static Boolean bool(String text) {
    return switch (text.toLowerCase()) {
      case "true", "t", "1", "yes" -> true;
      case "false", "f", "0", "no" -> false;
      default -> throw new NumberFormatException(text);
    };
  }
}
