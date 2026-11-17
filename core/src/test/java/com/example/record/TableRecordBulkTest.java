package com.example.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TableRecordBulkTest {

  private final MockDb db = new MockDb();

  private TableRecord modules() {
    return new TableRecord(db, "js_modules");
  }

  private String firstStatement(String prefix) {
    return db.sql.stream().filter(s -> s.startsWith(prefix)).findFirst().orElseThrow();
  }

  @Test
  void orderByCallsAddColumnsInOrder() {
    TableRecord gr = modules();
    gr.orderBy("name");
    gr.orderByDesc("content");
    gr.query();
    String select = firstStatement("select");
    assertTrue(select.contains("order by"), select);
    assertTrue(select.indexOf("\"name\"") < select.indexOf("\"content\" desc"), select);
  }

  @Test
  void updateMultipleWritesTheMatchingRowAndRepeatsTheConditions() {
    db.row("a", "old");
    TableRecord gr = modules();
    gr.addQuery("name", "a");
    gr.setValue("content", "new");
    assertEquals(1, gr.updateMultiple());
    String update = firstStatement("update");
    assertTrue(update.contains("\"content\""), update);
    assertEquals(
        2, update.split("\"name\" =", -1).length - 1, "key and query condition: " + update);
  }

  @Test
  void updateMultipleSkipsTheWriteWhenNothingChanges() {
    db.row("a", "same");
    TableRecord gr = modules();
    gr.addQuery("name", "a");
    gr.setValue("content", "same");
    assertEquals(1, gr.updateMultiple());
    assertEquals(0, db.count("update"));
  }

  @Test
  void updateMultipleRunsBeforeAndAfterRuleLookups() {
    db.row("a", "old");
    TableRecord gr = modules();
    gr.addQuery("name", "a");
    gr.setValue("content", "new");
    gr.updateMultiple();
    assertEquals(2, db.sql.stream().filter(s -> s.contains("business_rules")).count());
  }

  @Test
  void aBeforeRuleThatAbortsSkipsTheRow() {
    db.row("a", "old");
    db.rule("block", null, "current.setAbortAction(true);");
    TableRecord gr = modules();
    gr.addQuery("name", "a");
    gr.setValue("content", "new");
    assertEquals(0, gr.updateMultiple());
    assertEquals(0, db.count("update"));
  }

  @Test
  void deleteMultipleDeletesTheMatchingRowAndRepeatsTheConditions() {
    db.row("a", "x");
    TableRecord gr = modules();
    gr.addQuery("name", "a");
    assertEquals(1, gr.deleteMultiple());
    assertTrue(firstStatement("delete").contains("\"name\" ="));
  }

  @Test
  void bulkWritesRefuseToRunWithoutConditions() {
    TableRecord update = modules();
    update.setValue("content", "x");
    assertThrows(IllegalStateException.class, update::updateMultiple);
    assertThrows(IllegalStateException.class, () -> modules().deleteMultiple());
  }

  @Test
  void countReadsTheCountFromTheDatabase() {
    db.row("3", "x");
    TableRecord gr = modules();
    gr.addQuery("name", "!=", "z");
    assertEquals(3, gr.count());
    assertTrue(firstStatement("select").contains("count"));
  }
}
