package com.example.record;

import static org.junit.jupiter.api.Assertions.*;

import com.example.model.BusinessRule;
import com.example.script.ScriptExecutor;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BusinessRulesTest {

  private final MockDb db = new MockDb();
  private final BusinessRules rules = new BusinessRules(db);

  private static BusinessRule valid() {
    return BusinessRule.builder()
        .name("No empty modules")
        .collection("js_modules")
        .runWhen("before")
        .onInsert(true)
        .script("current.setAbortAction(true);")
        .build();
  }

  private static Map<String, Object> fields() {
    Map<String, Object> f = new HashMap<>();
    f.put("name", "No empty modules");
    f.put("collection", "js_modules");
    f.put("runWhen", "before");
    f.put("onInsert", true);
    f.put("script", "current.setAbortAction(true);");
    return f;
  }

  // --- validate ---

  @Test
  void validRulePasses() {
    assertDoesNotThrow(() -> rules.validate(valid()));
  }

  @Test
  void validateRejectsEachMissingPart() {
    BusinessRule noName = valid();
    noName.setName(" ");
    BusinessRule badTable = valid();
    badTable.setCollection("a b");
    BusinessRule badWhen = valid();
    badWhen.setRunWhen("during");
    BusinessRule noOperation = valid();
    noOperation.setOnInsert(false);
    BusinessRule noScript = valid();
    noScript.setScript("");
    for (BusinessRule bad : new BusinessRule[] {noName, badTable, badWhen, noOperation, noScript}) {
      assertThrows(IllegalArgumentException.class, () -> rules.validate(bad));
    }
  }

  @Test
  void syntaxErrorInScriptFailsBeforeSaving() {
    BusinessRule rule = valid();
    rule.setScript("var a = ;");
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> rules.create(rule));
    assertTrue(e.getMessage().contains("does not compile"));
    assertTrue(db.sql.isEmpty());
  }

  @Test
  void syntaxErrorInConditionFails() {
    BusinessRule rule = valid();
    rule.setCondition("current.getValue('x') ==");
    assertThrows(IllegalArgumentException.class, () -> rules.validate(rule));
  }

  // --- create, update, delete ---

  @Test
  void createInsertsAndReturnsKey() {
    assertEquals("new-key", rules.create(fields()));
    assertEquals(1, db.count("insert into \"business_rules\""));
  }

  @Test
  void unknownFieldIsRejected() {
    Map<String, Object> f = fields();
    f.put("colour", "red");
    assertThrows(IllegalArgumentException.class, () -> rules.create(f));
    assertTrue(db.sql.isEmpty());
  }

  @Test
  void badIdIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> rules.get("abc"));
  }

  @Test
  void updateOfMissingRuleThrows() {
    assertThrows(IllegalArgumentException.class, () -> rules.update("7", Map.of("active", false)));
  }

  @Test
  void deleteOfMissingRuleIsFalseAndWritesNothing() {
    assertFalse(rules.delete("7"));
    assertEquals(0, db.count("delete from"));
  }

  @Test
  void deleteRemovesTheRow() {
    // The mock answers any select on business_rules with a rule row, so one row is "found".
    db.rule("existing", null, "var ok = 1;");
    assertTrue(rules.delete("7"));
    assertEquals(1, db.count("delete from"));
  }

  // --- scripts ---

  @Test
  void scriptsSeeTheBusinessRulesGlobal() {
    ScriptExecutor.ScriptExecutionResult result =
        new ScriptExecutor().execute("typeof BusinessRules", null);
    assertTrue(result.isSuccess());
    assertEquals("object", String.valueOf(result.getResult()));
  }

  @Test
  void scriptValidateAcceptsGoodAndRejectsBadSource() {
    String good =
        "BusinessRules.validate({name:'r', collection:'js_modules', runWhen:'before',"
            + " onInsert:true, script:'var a = 1;'}); 'ok'";
    String bad = good.replace("var a = 1;", "var a = ;");
    ScriptExecutor executor = new ScriptExecutor();
    assertTrue(executor.execute(good, null).isSuccess());
    assertFalse(executor.execute(bad, null).isSuccess());
  }
}
