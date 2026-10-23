package com.example.record;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TableRecordTest {

  private final MockDb db = new MockDb();

  private TableRecord modules() {
    return new TableRecord(db, "js_modules");
  }

  // --- guards ---

  @Test
  void rejectsTableOutsideAllowList() {
    assertThrows(IllegalArgumentException.class, () -> new TableRecord(db, "sys_secrets"));
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
