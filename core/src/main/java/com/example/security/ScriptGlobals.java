package com.example.security;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * Names that every script sees as globals. A module registers a global at startup, so core never
 * needs to know the module. Registering a global also makes its classes visible to the sandbox.
 */
public final class ScriptGlobals {

  private static final Map<String, Supplier<Object>> FACTORIES = new ConcurrentHashMap<>();
  private static final Set<String> VISIBLE = ConcurrentHashMap.newKeySet();

  private ScriptGlobals() {}

  /**
   * Registers a global. A later call with the same name replaces the first.
   *
   * @param name the name scripts use, for example {@code Jobs}
   * @param factory builds the value for each script run
   * @param visible the classes scripts may touch through the value
   */
  public static void register(String name, Supplier<Object> factory, Class<?>... visible) {
    FACTORIES.put(name, factory);
    for (Class<?> type : visible) {
      VISIBLE.add(type.getName());
    }
  }

  /**
   * Puts every registered global into a script scope.
   *
   * @param scope the scope of the script about to run
   */
  public static void apply(Scriptable scope) {
    FACTORIES.forEach(
        (name, factory) ->
            ScriptableObject.putProperty(scope, name, Context.javaToJS(factory.get(), scope)));
  }

  /**
   * Tells whether a registered global exposes a class.
   *
   * @param className full class name
   * @return true when scripts may use the class
   */
  static boolean isVisible(String className) {
    return VISIBLE.contains(className);
  }

  /** Forgets every registration. For tests. */
  static void clear() {
    FACTORIES.clear();
    VISIBLE.clear();
  }
}
