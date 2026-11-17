package com.example.record;

import com.example.config.Database;
import java.io.IOException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * Runs business rule scripts. The script engine implements it and registers as a service, so the
 * record layer never imports the script package.
 */
public interface RuleScriptEngine {

  /** Binds the names a rule sees, such as {@code current}, into the script scope. */
  @FunctionalInterface
  interface ScopeBinder {
    void bind(Scriptable scope, Context cx);
  }

  /** What one script run produced. */
  final class Outcome {
    private final boolean success;
    private final Object result;
    private final String message;
    private final Throwable throwable;

    public Outcome(boolean success, Object result, String message, Throwable throwable) {
      this.success = success;
      this.result = result;
      this.message = message;
      this.throwable = throwable;
    }

    public boolean isSuccess() {
      return success;
    }

    public Object getResult() {
      return result;
    }

    public String getMessage() {
      return message;
    }

    public Throwable getThrowable() {
      return throwable;
    }
  }

  /**
   * Runs a script in the sandbox.
   *
   * @param source the script text
   * @param binder binds the rule's names into the scope
   * @return the outcome, never null
   */
  Outcome evaluate(String source, ScopeBinder binder);

  /**
   * Resolves module imports in a script.
   *
   * @param source the script text
   * @param database database holding the modules
   * @return the script with its imports resolved
   * @throws IOException when a module cannot be read
   */
  String prepareModules(String source, Database database) throws IOException;
}
