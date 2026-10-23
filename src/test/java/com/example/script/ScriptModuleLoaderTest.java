package com.example.script;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.model.Module;
import com.example.module.ModuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScriptModuleLoaderTest {

  private ModuleManager manager;
  private ScriptModuleLoader loader;

  @BeforeEach
  void setUp() {
    manager = mock(ModuleManager.class);
    loader = new ScriptModuleLoader(manager);
  }

  private void module(String name, String content) throws Exception {
    when(manager.getModule(name)).thenReturn(new Module(name, name + ".js", content));
  }

  @Test
  void rewritesDefaultImport() throws Exception {
    module("lib/a", "module.exports = { x: 1 };");

    String out = loader.prepareWithModules("import a from 'lib/a';\na.x;");

    assertTrue(out.contains("var a = require('lib/a');"), out);
    assertFalse(out.contains("import a from"), out);
    assertTrue(out.contains("__moduleCache['lib/a']"), out);
  }

  @Test
  void proseAboutImportDoesNotSwallowTheNextStatement() throws Exception {
    module("lib/a", "module.exports = {};");
    String script = "// Shows import usage\n\nimport a from 'lib/a';\nvar y = 1;";

    String out = loader.prepareWithModules(script);

    assertTrue(out.contains("// Shows import usage"), out);
    assertTrue(out.contains("var a = require('lib/a');"), out);
    assertFalse(out.contains("var usage"), out);
  }

  @Test
  void importInsideAModuleBodyIsRewritten() throws Exception {
    module("lib/a", "module.exports = { x: 1 };");
    module("lib/m", "import a from 'lib/a';\nmodule.exports = { value: a.x + 2 };");

    String out = loader.prepareWithModules("import m from 'lib/m';\nm.value;");

    assertFalse(out.contains("import a from"), out);
    assertTrue(out.contains("var a = require('lib/a');"), out);
  }

  @Test
  void aModuleThatImportsAnotherRunsInRhino() throws Exception {
    module("lib/a", "module.exports = { x: 1 };");
    module("lib/m", "import a from 'lib/a';\nmodule.exports = { value: a.x + 2 };");

    String prepared = loader.prepareWithModules("import m from 'lib/m';\nm.value;");
    ScriptExecutor.ScriptExecutionResult result = new ScriptExecutor().execute(prepared, null);

    assertTrue(result.isSuccess(), result.getMessage());
    assertEquals(3, ((Number) result.getResult()).intValue());
  }

  @Test
  void missingModuleFailsBeforeRunning() {
    assertThrows(
        java.io.IOException.class, () -> loader.prepareWithModules("import a from 'lib/nope';"));
  }
}
