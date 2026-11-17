package com.example.record;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AsyncRulesTest {

  private final MockDb db = new MockDb();

  {
    // The key is not a field the tests read, so an insert cannot overwrite one.
    db.key("id");
  }

  private final List<AsyncRuleCall> queued = new ArrayList<>();

  @BeforeEach
  void plugInSink() {
    AsyncRules.setSink(queued::add);
  }

  @AfterEach
  void removeSink() {
    AsyncRules.setSink(null);
  }

  private TableRecord modules() {
    return new TableRecord(db, "js_modules");
  }

  @Test
  void anInsertQueuesTheMatchingRuleWithTheSavedFields() {
    db.asyncRule("7", "Notify", null, "var ok = 1;");
    TableRecord gr = modules();
    gr.setValue("name", "utils/new");
    gr.insert();

    assertEquals(1, queued.size());
    AsyncRuleCall call = queued.get(0);
    assertEquals("7", call.ruleId);
    assertEquals("Notify", call.ruleName);
    assertEquals("js_modules", call.table);
    assertEquals("insert", call.operation);
    assertEquals("utils/new", call.current.get("name"));
    assertNull(call.previous);
    assertEquals(1, call.depth);
  }

  @Test
  void anUpdateCarriesTheRowBeforeTheWrite() {
    db.row("utils/a", "old");
    db.asyncRule("7", "Notify", null, "var ok = 1;");
    TableRecord gr = modules();
    gr.query();
    gr.next();
    gr.setValue("content", "new");
    gr.update();

    AsyncRuleCall call = queued.get(0);
    assertEquals("update", call.operation);
    assertEquals("new", call.current.get("content"));
    assertEquals("old", call.previous.get("content"));
  }

  @Test
  void aFalseConditionQueuesNothing() {
    db.asyncRule("7", "Only big", "current.getValue('name') == 'nope'", "var ok = 1;");
    TableRecord gr = modules();
    gr.setValue("name", "utils/new");
    gr.insert();
    assertTrue(queued.isEmpty());
  }

  @Test
  void aTrueConditionQueuesTheRule() {
    db.asyncRule("7", "Match", "current.getValue('name') == 'utils/new'", "var ok = 1;");
    TableRecord gr = modules();
    gr.setValue("name", "utils/new");
    gr.insert();
    assertEquals(1, queued.size());
  }

  @Test
  void aBeforeRuleThatAbortsQueuesNothing() {
    db.rule("block", null, "current.setAbortAction(true);");
    db.asyncRule("7", "Notify", null, "var ok = 1;");
    TableRecord gr = modules();
    gr.setValue("name", "utils/new");
    assertNull(gr.insert());
    assertTrue(queued.isEmpty());
  }

  @Test
  void noSinkNeverFailsTheWrite() {
    AsyncRules.setSink(null);
    db.asyncRule("7", "Notify", null, "var ok = 1;");
    TableRecord gr = modules();
    gr.setValue("name", "utils/new");
    assertEquals("new-key", gr.insert());
  }

  @Test
  void aFailingSinkNeverFailsTheWrite() {
    AsyncRules.setSink(
        call -> {
          throw new IllegalStateException("queue is down");
        });
    db.asyncRule("7", "Notify", null, "var ok = 1;");
    TableRecord gr = modules();
    gr.setValue("name", "utils/new");
    assertEquals("new-key", gr.insert());
  }

  @Test
  void aSnapshotCannotWrite() {
    TableRecord snapshot =
        TableRecord.snapshot(db, "js_modules", java.util.Map.of("name", "utils/a"));
    assertEquals("utils/a", snapshot.getValue("name"));
    assertThrows(IllegalStateException.class, snapshot::update);
    assertThrows(IllegalStateException.class, snapshot::deleteRecord);
    assertThrows(IllegalStateException.class, snapshot::insert);
  }

  @Test
  void validateAcceptsAsync() {
    BusinessRule_ rule = new BusinessRule_();
    assertDoesNotThrow(() -> new BusinessRules(db).validate(rule.async()));
  }

  /** Builds a valid async rule. */
  private static class BusinessRule_ {
    com.example.model.BusinessRule async() {
      return com.example.model.BusinessRule.builder()
          .name("Notify")
          .collection("js_modules")
          .runWhen("async")
          .onInsert(true)
          .script("var ok = 1;")
          .build();
    }
  }
}
