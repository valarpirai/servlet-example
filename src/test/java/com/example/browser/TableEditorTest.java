package com.example.browser;

import static org.junit.jupiter.api.Assertions.*;

import com.example.config.Database;
import com.example.util.PropertiesUtil;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TableEditorTest {

  private static final Map<String, String> TYPES =
      Map.of(
          "name", "text",
          "hits", "bigint",
          "price", "numeric",
          "on", "boolean",
          "at", "timestamp with time zone",
          "doc", "jsonb");

  /** Statements sent, lowercased. */
  private final List<String> sql = new ArrayList<>();

  private final List<Object[]> rules = new ArrayList<>();
  private final List<Object[]> rows = new ArrayList<>();
  private String tableName = "js_modules";
  private TableEditor editor;

  @BeforeEach
  void setUp() {
    // YAML-only: the allow-list is the built-in default, js_modules:name
    PropertiesUtil.setPropertyRepository(null);
    editor = new TableEditor(new FakeDb());
  }

  /** Answers the metadata, the rule lookup, row reads, inserts and updates by statement text. */
  private class FakeDb extends Database {
    FakeDb() {
      super(null);
    }

    @Override
    public <T> T query(Work<T> fn) throws Exception {
      return fn.run(
          DSL.using(
              new MockConnection(
                  ctx -> {
                    String s = ctx.sql().toLowerCase();
                    sql.add(s);
                    if (s.contains("group by t.table_name")) {
                      return result(List.<Object[]>of(new Object[] {tableName, 2L}), "t", "c");
                    }
                    if (s.contains("key_column_usage")) {
                      return result(List.<Object[]>of(new Object[] {"name"}), "column_name");
                    }
                    if (s.contains("information_schema.columns")) {
                      return result(
                          List.of(
                              new Object[] {"name", "text"},
                              new Object[] {"content", "text"},
                              new Object[] {"hits", "bigint"}),
                          "column_name",
                          "data_type");
                    }
                    if (s.startsWith("select") && s.contains("business_rules")) {
                      return result(rules, "name", "condition", "script");
                    }
                    if (s.startsWith("select")) {
                      return result(rows, "name", "content", "hits");
                    }
                    if (s.startsWith("insert")) {
                      return result(List.<Object[]>of(new Object[] {"made"}), "name");
                    }
                    return new MockResult[] {new MockResult(1)};
                  }),
              SQLDialect.POSTGRES));
    }
  }

  @SuppressWarnings("unchecked")
  private static MockResult[] result(List<Object[]> data, String... columns) {
    var create = DSL.using(SQLDialect.POSTGRES);
    Field<?>[] fields = new Field<?>[columns.length];
    for (int i = 0; i < columns.length; i++) {
      fields[i] = DSL.field(DSL.name(columns[i]), Object.class);
    }
    Result<Record> result = create.newResult(fields);
    for (Object[] row : data) {
      Record record = create.newRecord(fields);
      for (int i = 0; i < fields.length; i++) {
        record.set((Field<Object>) fields[i], row[i]);
      }
      result.add(record);
    }
    return new MockResult[] {new MockResult(result.size(), result)};
  }

  private long count(String prefix) {
    return sql.stream().filter(s -> s.startsWith(prefix)).count();
  }

  // --- coerce ---

  @Test
  void coercesTextToTheColumnType() {
    assertEquals("a", TableEditor.coerce("name", TYPES, "a"));
    assertEquals(42L, TableEditor.coerce("hits", TYPES, " 42 "));
    assertEquals(new BigDecimal("1.50"), TableEditor.coerce("price", TYPES, "1.50"));
    assertEquals(true, TableEditor.coerce("on", TYPES, "true"));
    assertEquals(false, TableEditor.coerce("on", TYPES, "f"));
    assertEquals(
        OffsetDateTime.parse("2026-01-02T03:04:05Z"),
        TableEditor.coerce("at", TYPES, "2026-01-02T03:04:05Z"));
  }

  @Test
  void jsonNumbersAreWholeNumbersWhenTheyAreWhole() {
    assertEquals(7L, TableEditor.coerce("hits", TYPES, 7.0));
  }

  @Test
  void blankIsNullExceptInTextColumns() {
    assertEquals("", TableEditor.coerce("name", TYPES, ""));
    assertNull(TableEditor.coerce("hits", TYPES, " "));
    assertNull(TableEditor.coerce("hits", TYPES, null));
  }

  @Test
  void rejectsBadValuesUnknownColumnsAndUneditableTypes() {
    assertThrows(IllegalArgumentException.class, () -> TableEditor.coerce("hits", TYPES, "abc"));
    assertThrows(IllegalArgumentException.class, () -> TableEditor.coerce("on", TYPES, "maybe"));
    assertThrows(IllegalArgumentException.class, () -> TableEditor.coerce("at", TYPES, "soon"));
    assertThrows(IllegalArgumentException.class, () -> TableEditor.coerce("nope", TYPES, "x"));
    assertThrows(IllegalArgumentException.class, () -> TableEditor.coerce("doc", TYPES, "{}"));
  }

  // --- create ---

  @Test
  void createInsertsAndReturnsTheKey() throws Exception {
    String key = editor.create("js_modules", Map.of("name", "m", "hits", "3"));

    assertEquals("made", key);
    assertEquals(1, count("insert"));
  }

  @Test
  void createLeavesOutBlankNonTextFields() throws Exception {
    editor.create("js_modules", Map.of("name", "m", "hits", ""));

    String insert = sql.stream().filter(s -> s.startsWith("insert")).findFirst().orElseThrow();
    assertFalse(insert.contains("hits"), insert);
  }

  @Test
  void createIsBlockedByABeforeRule() {
    rules.add(new Object[] {"stop", "true", "current.setAbortAction(true);"});

    assertThrows(
        TableEditor.BlockedException.class, () -> editor.create("js_modules", Map.of("name", "m")));
    assertEquals(0, count("insert"));
  }

  @Test
  void createRefusesATableThatIsNotAllowListed() {
    tableName = "notes";

    assertThrows(
        TableEditor.NotWritableException.class, () -> editor.create("notes", Map.of("name", "m")));
  }

  @Test
  void createRefusesAnUnknownTable() {
    tableName = "other";

    assertThrows(
        IllegalArgumentException.class, () -> editor.create("js_modules", Map.of("name", "m")));
  }

  // --- update ---

  @Test
  void updateWritesTheChangedFields() throws Exception {
    rows.add(new Object[] {"m", "old", 1L});

    assertTrue(editor.update("js_modules", "m", Map.of("content", "new")));

    assertEquals(1, count("update"));
  }

  @Test
  void updateReportsAMissingRow() throws Exception {
    assertFalse(editor.update("js_modules", "gone", Map.of("content", "new")));
    assertEquals(0, count("update"));
  }

  @Test
  void updateCannotChangeTheKey() {
    rows.add(new Object[] {"m", "old", 1L});

    assertThrows(
        IllegalArgumentException.class,
        () -> editor.update("js_modules", "m", Map.of("name", "x")));
  }

  @Test
  void updateIsBlockedByABeforeRule() {
    rows.add(new Object[] {"m", "old", 1L});
    rules.add(new Object[] {"stop", "true", "current.setAbortAction(true);"});

    assertThrows(
        TableEditor.BlockedException.class,
        () -> editor.update("js_modules", "m", Map.of("content", "new")));
    assertEquals(0, count("update"));
  }

  // --- delete ---

  @Test
  void deleteRemovesEachRow() throws Exception {
    rows.add(new Object[] {"m", "old", 1L});

    Map<String, Object> out = editor.delete("js_modules", List.of("m", "m"));

    assertEquals(2, out.get("deleted"));
    assertEquals(2, count("delete"));
  }

  @Test
  void deleteReportsRowsARuleBlocks() throws Exception {
    rows.add(new Object[] {"m", "old", 1L});
    rules.add(new Object[] {"stop", "true", "current.setAbortAction(true);"});

    Map<String, Object> out = editor.delete("js_modules", List.of("m"));

    assertEquals(0, out.get("deleted"));
    assertEquals(1, ((List<?>) out.get("failed")).size());
    assertEquals(0, count("delete"));
  }

  @Test
  void deleteReportsMissingRowsAndKeepsGoing() throws Exception {
    Map<String, Object> out = editor.delete("js_modules", List.of("a", "b"));

    assertEquals(0, out.get("deleted"));
    assertEquals(2, ((List<?>) out.get("failed")).size());
  }
}
