package com.example.processor;

import com.example.config.Database;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;

/**
 * In-memory stand-in for the tables that hold scripts. {@link #endpoint} fills {@code api_scripts}
 * and {@link #module} fills {@code modules}. A select by name finds the row or returns nothing.
 */
public class FakeScriptDb extends Database {

  private static final org.jooq.DSLContext CREATE = DSL.using(SQLDialect.POSTGRES);

  private final Map<String, String> endpoints = new HashMap<>();
  private final Map<String, String> modules = new HashMap<>();

  public FakeScriptDb() {
    super(null);
  }

  /** Stores an endpoint script, for example {@code endpoint("hello", "function httpHandler..")}. */
  public void endpoint(String name, String script) {
    endpoints.put(name, script);
  }

  /** Stores a module, for example {@code module("lib/utils", "module.exports = {};")}. */
  public void module(String name, String content) {
    modules.put(name, content);
  }

  @Override
  public <T> T query(Work<T> fn) throws Exception {
    return fn.run(
        DSL.using(
            new MockConnection(
                ctx -> {
                  String sql = ctx.sql().toLowerCase();
                  Object name = ctx.bindings()[0];
                  if (sql.contains("api_scripts")) {
                    String content = endpoints.get(name);
                    return rows(
                        content == null
                            ? List.<Object[]>of()
                            : List.<Object[]>of(new Object[] {content}),
                        "content");
                  }
                  String content = modules.get(name);
                  return rows(
                      content == null
                          ? List.<Object[]>of()
                          : List.<Object[]>of(
                              new Object[] {
                                name,
                                content,
                                content.length(),
                                OffsetDateTime.now(),
                                OffsetDateTime.now()
                              }),
                      "name",
                      "content",
                      "size_bytes",
                      "created_at",
                      "updated_at");
                }),
            SQLDialect.POSTGRES));
  }

  @SuppressWarnings("unchecked")
  private static MockResult[] rows(List<Object[]> data, String... columns) {
    Field<?>[] fields = new Field<?>[columns.length];
    for (int i = 0; i < columns.length; i++) {
      fields[i] = DSL.field(DSL.name(columns[i]), Object.class);
    }
    Result<Record> result = CREATE.newResult(fields);
    for (Object[] row : data) {
      Record record = CREATE.newRecord(fields);
      for (int i = 0; i < fields.length; i++) {
        record.set((Field<Object>) fields[i], row[i]);
      }
      result.add(record);
    }
    return new MockResult[] {new MockResult(result.size(), result)};
  }
}
