package com.example.script;

import com.example.config.Database;
import com.example.module.ModuleManager;
import com.example.record.RuleScriptEngine;
import java.io.IOException;

/** Runs business rule scripts in the Rhino sandbox. Registered as a {@link RuleScriptEngine}. */
public class ScriptRuleEngine implements RuleScriptEngine {

  private final ScriptExecutor executor = new ScriptExecutor();

  @Override
  public Outcome evaluate(String source, ScopeBinder binder) {
    ScriptExecutor.ScriptExecutionResult result =
        executor.execute(source, (scope, cx) -> binder.bind(scope, cx));
    return new Outcome(
        result.isSuccess(), result.getResult(), result.getMessage(), result.getThrowable());
  }

  @Override
  public String prepareModules(String source, Database database) throws IOException {
    return new ScriptModuleLoader(new ModuleManager(database)).prepareWithModules(source);
  }
}
