package com.example.security;

import static org.junit.jupiter.api.Assertions.*;

import com.example.script.ScriptExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScriptGlobalsTest {

  /** A value a module might register. */
  public static class Greeter {
    public String hello(String name) {
      return "hello " + name;
    }
  }

  @AfterEach
  void reset() {
    ScriptGlobals.clear();
  }

  @Test
  void registeredGlobalIsCallableFromScripts() {
    ScriptGlobals.register("Greeter", Greeter::new, Greeter.class);
    ScriptExecutor.ScriptExecutionResult result =
        new ScriptExecutor().execute("Greeter.hello('world')", null);
    assertTrue(result.isSuccess(), result.getMessage());
    assertEquals("hello world", String.valueOf(result.getResult()));
  }

  @Test
  void registeringMakesTheClassVisible() {
    assertFalse(ScriptGlobals.isVisible(Greeter.class.getName()));
    ScriptGlobals.register("Greeter", Greeter::new, Greeter.class);
    assertTrue(ScriptGlobals.isVisible(Greeter.class.getName()));
    assertTrue(
        new ScriptSecurityManager.SandboxClassShutter().visibleToScripts(Greeter.class.getName()));
  }

  @Test
  void unregisteredGlobalIsNotDefined() {
    assertFalse(new ScriptExecutor().execute("Greeter.hello('x')", null).isSuccess());
  }
}
