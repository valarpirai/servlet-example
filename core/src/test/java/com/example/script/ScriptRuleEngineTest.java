package com.example.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.processor.FakeScriptDb;
import com.example.record.RuleScriptEngine;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ScriptableObject;

class ScriptRuleEngineTest {

  private final RuleScriptEngine engine = new ScriptRuleEngine();

  @Test
  void isRegisteredAsAService() {
    RuleScriptEngine found = ServiceLoader.load(RuleScriptEngine.class).findFirst().orElseThrow();

    assertTrue(found instanceof ScriptRuleEngine);
  }

  @Test
  void evaluateReturnsTheScriptResult() {
    RuleScriptEngine.Outcome outcome = engine.evaluate("1 + 1", (scope, cx) -> {});

    assertTrue(outcome.isSuccess());
    assertEquals(2, ((Number) outcome.getResult()).intValue());
  }

  @Test
  void evaluateSeesBoundNames() {
    RuleScriptEngine.Outcome outcome =
        engine.evaluate(
            "answer * 2", (scope, cx) -> ScriptableObject.putProperty(scope, "answer", 21));

    assertEquals(42, ((Number) outcome.getResult()).intValue());
  }

  @Test
  void evaluateReportsAScriptError() {
    RuleScriptEngine.Outcome outcome = engine.evaluate("throw new Error('boom')", (s, c) -> {});

    assertFalse(outcome.isSuccess());
    assertTrue(outcome.getMessage().contains("boom"), outcome.getMessage());
  }

  @Test
  void prepareModulesKeepsAScriptWithoutImportsIntact() throws Exception {
    String prepared = engine.prepareModules("var x = 1;", new FakeScriptDb());

    assertTrue(prepared.startsWith("var __moduleCache = {};"), prepared);
    assertTrue(prepared.endsWith("var x = 1;"), prepared);
  }
}
