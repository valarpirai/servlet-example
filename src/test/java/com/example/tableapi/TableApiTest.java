package com.example.tableapi;

import static org.junit.jupiter.api.Assertions.*;

import com.example.config.Database;
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
import org.junit.jupiter.api.Test;

class TableApiTest {

  /** Statements sent, lowercased. */
  private final List<String> sql = new ArrayList<>();

  private List<Object[]> tables = List.<Object[]>of(new Object[] {"notes", 3L});
  private List<Object[]> keys = List.<Object[]>of(new Object[] {"id"});
  private List<Object[]> data = new ArrayList<>();
  private long count = 0;

  /** Bind values of every statement sent. */
  private final List<Object[]> bindings = new ArrayList<>();

  private final TableApi browser = new TableApi(new FakeDb());

  /** Answers the metadata queries and the data query by statement text. */
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
                    bindings.add(ctx.bindings());
                    if (s.contains("group by t.table_name")) {
                      return result(tables, "table_name", "column_count");
                    }
                    if (s.contains("key_column_usage")) {
                      return result(keys, "column_name");
                    }
                    if (s.contains("information_schema.columns")) {
                      return result(
                          List.of(
                              new Object[] {"id", "bigint"},
                              new Object[] {"body", "text"},
                              new Object[] {"blob", "bytea"}),
                          "column_name",
                          "data_type");
                    }
                    if (s.startsWith("select count(")) {
                      return result(List.<Object[]>of(new Object[] {count}), "count");
                    }
                    return result(data, "id", "body", "blob");
                  }),
              SQLDialect.POSTGRES));
    }
  }

  @SuppressWarnings("unchecked")
  private static MockResult[] result(List<Object[]> rows, String... columns) {
    var create = DSL.using(SQLDialect.POSTGRES);
    Field<?>[] fields = new Field<?>[columns.length];
    for (int i = 0; i < columns.length; i++) {
      fields[i] = DSL.field(DSL.name(columns[i]), Object.class);
    }
    Result<Record> result = create.newResult(fields);
    for (Object[] row : rows) {
      Record record = create.newRecord(fields);
      for (int i = 0; i < fields.length; i++) {
        record.set((Field<Object>) fields[i], row[i]);
      }
      result.add(record);
    }
    return new MockResult[] {new MockResult(result.size(), result)};
  }

  // --- listTables ---

  @Test
  void listTables_returnsNameAndColumnCount() throws Exception {
    var list = browser.listTables();
    assertEquals(1, list.size());
    assertEquals("notes", list.get(0).get("name"));
    assertEquals(3L, list.get(0).get("columns"));
  }

  // --- rows ---

  @Test
  void rows_unknownTableIsEmpty() throws Exception {
    assertTrue(browser.rows("missing", 1, 0, null, false).isEmpty());
  }

  @Test
  void rows_neverQueriesAnInjectedName() throws Exception {
    assertTrue(browser.rows("notes\"; drop table notes; --", 1, 0, null, false).isEmpty());
    assertTrue(sql.stream().noneMatch(s -> s.contains("drop table")));
    assertEquals(1, sql.size(), "only the table list was read");
  }

  @Test
  void rows_cutsLongTextAndSummarizesBinary() throws Exception {
    data = List.<Object[]>of(new Object[] {1L, "x".repeat(500), new byte[] {1, 2, 3}});
    count = 1;

    Map<String, Object> out = browser.rows("notes", 1, 0, null, false).orElseThrow();
    @SuppressWarnings("unchecked")
    var row = ((List<Map<String, Object>>) out.get("rows")).get(0);

    assertEquals(TableApi.LIST_CELL_MAX + 1, ((String) row.get("body")).length());
    assertTrue(((String) row.get("body")).endsWith("…"));
    assertEquals("<binary 3 bytes>", row.get("blob"));
    assertEquals(1L, row.get("id"));
    assertEquals("id", out.get("primaryKey"));
    assertEquals(1L, out.get("total"));
  }

  @Test
  void rows_quotesTableAndOrdersByKey() throws Exception {
    browser.rows("notes", 1, 0, null, false);
    String select = sql.stream().filter(s -> s.startsWith("select *")).findFirst().orElseThrow();
    assertTrue(select.contains("from \"notes\""), select);
    assertTrue(select.contains("order by \"id\""), select);
  }

  // --- paging ---

  @Test
  void rows_pageThreeOfTenSkipsTwentyRows() throws Exception {
    Map<String, Object> out = browser.rows("notes", 3, 10, null, false).orElseThrow();
    String select = sql.stream().filter(s -> s.startsWith("select *")).findFirst().orElseThrow();
    assertTrue(select.contains("offset"), select);
    List<Integer> binds =
        java.util.Arrays.stream(bindings.get(sql.indexOf(select)))
            .map(b -> ((Number) b).intValue())
            .toList();
    assertEquals(List.of(20, 10), binds, "offset first, then page size");
    assertEquals(3, out.get("page"));
    assertEquals(10, out.get("size"));
  }

  @Test
  void rows_clampsPageAndSize() throws Exception {
    assertEquals(
        TableApi.MAX_PAGE_SIZE,
        browser.rows("notes", 1, 5000, null, false).orElseThrow().get("size"));
    assertEquals(
        TableApi.DEFAULT_PAGE_SIZE,
        browser.rows("notes", 1, 0, null, false).orElseThrow().get("size"));
    assertEquals(1, browser.rows("notes", -4, 10, null, false).orElseThrow().get("page"));
  }

  // --- sorting ---

  @Test
  void rows_sortsByColumnThenKey() throws Exception {
    Map<String, Object> out = browser.rows("notes", 1, 10, "body", true).orElseThrow();
    String select = sql.stream().filter(s -> s.startsWith("select *")).findFirst().orElseThrow();
    assertTrue(select.contains("order by \"body\" desc, \"id\" asc"), select);
    assertEquals("body", out.get("sort"));
    assertEquals(true, out.get("descending"));
  }

  @Test
  void rows_sortByKeyDoesNotRepeatTheKey() throws Exception {
    browser.rows("notes", 1, 10, "id", false);
    String select = sql.stream().filter(s -> s.startsWith("select *")).findFirst().orElseThrow();
    assertTrue(select.contains("order by \"id\" asc"), select);
    assertFalse(select.contains("\"id\" asc, \"id\""), select);
  }

  @Test
  void rows_rejectsUnknownSortColumn() {
    assertThrows(IllegalArgumentException.class, () -> browser.rows("notes", 1, 10, "nope", false));
  }

  @Test
  void rows_rejectsColumnTypesWithNoOrder() {
    assertThrows(IllegalArgumentException.class, () -> browser.rows("notes", 1, 10, "blob", false));
  }

  @Test
  void rows_neverQueriesAnInjectedSortColumn() {
    assertThrows(
        IllegalArgumentException.class,
        () -> browser.rows("notes", 1, 10, "body\"; drop table notes; --", false));
    assertTrue(sql.stream().noneMatch(s -> s.startsWith("select *")));
  }

  // --- row ---

  @Test
  void row_returnsFullText() throws Exception {
    data = List.<Object[]>of(new Object[] {1L, "x".repeat(500), new byte[] {1}});
    Map<String, Object> out = browser.row("notes", "1").orElseThrow();
    @SuppressWarnings("unchecked")
    var row = (Map<String, Object>) out.get("row");
    assertEquals(500, ((String) row.get("body")).length());
  }

  @Test
  void row_matchesKeyAsText() throws Exception {
    data = List.<Object[]>of(new Object[] {1L, "a", new byte[0]});
    browser.row("notes", "1");
    String select = sql.stream().filter(s -> s.startsWith("select *")).findFirst().orElseThrow();
    assertTrue(select.contains("cast(\"id\" as varchar)"), select);
  }

  @Test
  void row_missingRowIsEmpty() throws Exception {
    assertTrue(browser.row("notes", "99").isEmpty());
  }

  @Test
  void row_tableWithoutSingleKeyIsEmpty() throws Exception {
    keys = List.of();
    assertTrue(browser.row("notes", "1").isEmpty());
  }

  @Test
  void row_compositeKeyIsEmpty() throws Exception {
    keys = List.<Object[]>of(new Object[] {"a"}, new Object[] {"b"});
    assertTrue(browser.row("notes", "1").isEmpty());
  }
}
