package com.example.record;

import com.example.config.Database;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/** The {@code TableRecord} global in scripts: {@code new TableRecord('table')}. */
public class RecordConstructor extends BaseFunction {

  private final Database database;

  /** Builds records on the application database. */
  public RecordConstructor() {
    this(null);
  }

  RecordConstructor(Database database) {
    this.database = database;
  }

  @Override
  public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    return construct(cx, scope, args);
  }

  @Override
  public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
    String table = args.length > 0 ? String.valueOf(args[0]) : null;
    TableRecord record =
        database == null ? new TableRecord(table) : new TableRecord(database, table);
    return (Scriptable) Context.javaToJS(record, scope);
  }
}
