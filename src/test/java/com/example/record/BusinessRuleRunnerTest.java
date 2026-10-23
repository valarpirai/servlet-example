package com.example.record;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BusinessRuleRunnerTest {

  private final MockDb db = new MockDb();

  private TableRecord newModule() {
    TableRecord gr = new TableRecord(db, "modules");
    gr.setValue("name", "a/b");
    gr.setValue("content", "x");
    return gr;
  }

  @Test
  void beforeRuleAbortsInsert() {
    db.rule("block", null, "current.setAbortAction(true);");
    assertNull(newModule().insert());
    assertEquals(0, db.count("insert"));
  }

  @Test
  void ruleSeesNullPreviousOnInsert() {
    db.rule("insert-only", "previous === null", "current.setAbortAction(true);");
    assertNull(newModule().insert());
  }

  @Test
  void falseConditionSkipsScript() {
    db.rule("never", "current.getValue('name') == 'zzz'", "current.setAbortAction(true);");
    assertEquals("new-key", newModule().insert());
  }

  @Test
  void ruleCanChangeFieldBeforeWrite() {
    db.row("a/b", "old");
    db.rule("stamp", "current.changes('content')", "current.setValue('content', 'stamped');");
    TableRecord gr = new TableRecord(db, "modules");
    gr.query();
    gr.next();
    gr.setValue("content", "new");
    assertTrue(gr.update());
    assertEquals("stamped", gr.getValue("content"));
  }

  @Test
  void ruleSeesPreviousValueOnUpdate() {
    db.row("a/b", "old");
    db.rule("guard", "previous.getValue('content') == 'old'", "current.setAbortAction(true);");
    TableRecord gr = new TableRecord(db, "modules");
    gr.query();
    gr.next();
    gr.setValue("content", "new");
    assertFalse(gr.update());
    assertEquals(0, db.count("update"));
  }

  @Test
  void brokenScriptSurfacesAsError() {
    db.rule("broken", null, "this is not javascript");
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> newModule().insert());
    assertTrue(e.getMessage().contains("broken"));
  }

  @Test
  void ruleThatWritesRecordsRunsNestedRules() {
    // Every insert fires this rule, which inserts again. The depth cap must stop it.
    db.rule(
        "recurse",
        null,
        "var g = new TableRecord('modules'); g.setValue('name', 'n'); g.setValue('content', 'c');"
            + " g.insert();");
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> newModule().insert());
    assertTrue(e.getMessage().contains("nest deeper"), e.getMessage());
  }

  @Test
  void ruleCannotReachTablesOutsideAllowList() {
    db.rule("sneaky", null, "new TableRecord('app_properties');");
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> newModule().insert());
    assertTrue(e.getMessage().contains("not allowed"), e.getMessage());
  }
}
