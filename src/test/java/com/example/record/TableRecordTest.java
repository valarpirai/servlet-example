package com.example.record;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TableRecordTest {

  private final MockDb db = new MockDb();

  private TableRecord modules() {
    return new TableRecord(db, "js_modules");
  }

  // --- guards ---

  @AfterEach
  void resetAllowedTables() {
    AllowedTables.reset();
  }

  @Test
  void anyTableWorksWhenSettingUnset() {
    assertDoesNotThrow(() -> new TableRecord(db, "sys_secrets"));
  }

  @Test
  void unlistedTableRejectedWhenSettingSet() {
    AllowedTables.override(AllowedTables.parse("js_modules"));
    assertDoesNotThrow(() -> new TableRecord(db, "js_modules"));
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> new TableRecord(db, "sys_secrets"));
    assertTrue(e.getMessage().contains("sys_secrets"));
  }

  @Test
  void appTablesStayUsableWhenRestricted() {
    AllowedTables.override(AllowedTables.parse("orders"));
    for (String t :
        new String[] {
          "app_properties",
          "js_modules",
          "api_scripts",
          "routes",
          "business_rules",
          "scheduled_jobs",
          "scheduled_job_runs"
        }) {
      assertDoesNotThrow(() -> new TableRecord(db, t), t);
    }
    assertThrows(IllegalArgumentException.class, () -> new TableRecord(db, "sys_secrets"));
  }

  @Test
  void badTableNameRejectedBeforeAnySql() {
    assertThrows(IllegalArgumentException.class, () -> new TableRecord(db, "a\"b"));
    assertThrows(IllegalArgumentException.class, () -> new TableRecord(db, "a b"));
    assertTrue(db.sql.isEmpty());
  }

  @Test
  void readsNeedNoKey() {
    db.row("a", "x");
    TableRecord gr = modules();
    gr.query();
    assertTrue(gr.next());
    assertEquals(0, db.sql.stream().filter(q -> q.contains("information_schema")).count());
  }

  @Test
  void listedKeyOverridesDiscovery() {
    AllowedTables.override(AllowedTables.parse("js_modules:slug"));
    TableRecord gr = modules();
    gr.setValue("name", "n");
    gr.insert();
    assertEquals(0, db.sql.stream().filter(q -> q.contains("information_schema")).count());
    assertTrue(db.sql.stream().anyMatch(q -> q.contains("slug")));
  }

  @Test
  void writeWithoutSingleKeyThrowsNamingTheTable() {
    db.key();
    db.row("a", "x");
    TableRecord gr = modules();
    gr.setValue("name", "n");
    assertTrue(
        assertThrows(IllegalStateException.class, gr::insert).getMessage().contains("js_modules"));
    gr.addQuery("name", "a");
    assertThrows(IllegalStateException.class, gr::updateMultiple);
    assertThrows(IllegalStateException.class, gr::deleteMultiple);
    gr.query();
    gr.next();
    assertThrows(IllegalStateException.class, gr::update);
    assertThrows(IllegalStateException.class, gr::deleteRecord);
  }

  @Test
  void compositeKeyIsReadOnlyToo() {
    db.key("a", "b");
    TableRecord gr = modules();
    gr.setValue("name", "n");
    assertThrows(IllegalStateException.class, gr::insert);
  }

  @Test
  void getColumnNamesListsRowColumns() {
    db.row("a", "x");
    TableRecord gr = modules();
    assertEquals(java.util.List.of(), gr.getColumnNames());
    gr.query();
    gr.next();
    assertEquals(java.util.List.of("name", "content"), gr.getColumnNames());
  }

  @Test
  void rejectsBadColumnName() {
    assertThrows(IllegalArgumentException.class, () -> modules().addQuery("name\" or 1=1 --", "x"));
  }

  @Test
  void rejectsBadOperator() {
    assertThrows(
        IllegalArgumentException.class, () -> modules().addQuery("name", "; drop table", "x"));
  }

  @Test
  void nextWithoutRowThenUpdateThrows() {
    assertThrows(IllegalStateException.class, () -> modules().update());
  }

  // --- query ---

  @Test
  void queryWalksRowsAndReadsStrings() {
    db.row("utils/math", "var a;");
    db.row("utils/string", "var b;");
    TableRecord gr = modules();
    gr.addQuery("name", "LIKE", "utils/%");
    gr.orderBy("name");
    gr.setLimit(10);
    gr.query();

    assertTrue(gr.next());
    assertEquals("utils/math", gr.getValue("name"));
    assertTrue(gr.next());
    assertEquals("var b;", gr.getValue("content"));
    assertFalse(gr.next());

    String select = db.sql.get(0);
    assertTrue(select.contains("like"));
    assertTrue(select.contains("order by"));
    assertTrue(select.contains("fetch next"), select);
  }

  // --- insert ---

  @Test
  void insertReturnsKey() {
    TableRecord gr = modules();
    gr.setValue("name", "a/b");
    gr.setValue("content", "x");
    assertEquals("new-key", gr.insert());
    assertEquals(1, db.count("insert"));
  }

  // --- update ---

  @Test
  void updateWritesOnlyChangedFields() {
    db.row("a/b", "old");
    TableRecord gr = modules();
    gr.query();
    gr.next();
    gr.setValue("content", "new");
    assertTrue(gr.changes("content"));
    assertFalse(gr.changes("name"));
    assertTrue(gr.update());

    String update = db.sql.stream().filter(s -> s.startsWith("update")).findFirst().orElseThrow();
    String setClause = update.substring(0, update.indexOf(" where "));
    assertTrue(setClause.contains("\"content\""), update);
    assertFalse(setClause.contains("\"name\""), update);
  }

  @Test
  void updateWithNoChangeSkipsTheWrite() {
    db.row("a/b", "same");
    TableRecord gr = modules();
    gr.query();
    gr.next();
    gr.setValue("content", "same");
    assertTrue(gr.update());
    assertEquals(0, db.count("update"));
  }

  // --- delete ---

  @Test
  void deleteRemovesCurrentRow() {
    db.row("a/b", "x");
    TableRecord gr = modules();
    gr.query();
    gr.next();
    assertTrue(gr.deleteRecord());
    assertEquals(1, db.count("delete"));
  }
}
