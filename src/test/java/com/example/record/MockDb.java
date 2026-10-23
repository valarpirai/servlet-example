package com.example.record;

import com.example.config.Database;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;

/**
 * In-memory stand-in for {@link Database}. It answers by statement type: rule lookups return the
 * rules added with {@link #rule}, other selects return the rows added with {@link #row}, inserts
 * return the key {@code new-key}, and updates and deletes report one affected row.
 */
class MockDb extends Database {

  private static final DSLContext CREATE = DSL.using(SQLDialect.POSTGRES);

  /** Every statement sent, lowercased, in order. */
  final List<String> sql = new ArrayList<>();

  private final List<Object[]> rules = new ArrayList<>();
  private final List<Object[]> rows = new ArrayList<>();

  MockDb() {
    super(null);
  }

  /** Adds a rule row: name, condition, script. The mock returns it for any rule lookup. */
  void rule(String name, String condition, String script) {
    rules.add(new Object[] {name, condition, script});
  }

  /** Adds a modules row: name, content. */
  void row(String name, String content) {
    rows.add(new Object[] {name, content});
  }

  long count(String prefix) {
    return sql.stream().filter(s -> s.startsWith(prefix)).count();
  }

  @Override
  public <T> T query(Work<T> fn) throws Exception {
    return fn.run(
        DSL.using(
            new MockConnection(
                ctx -> {
                  String s = ctx.sql().toLowerCase();
                  sql.add(s);
                  if (s.startsWith("select") && s.contains("business_rules")) {
                    return result(rules, "name", "condition", "script");
                  }
                  if (s.startsWith("select")) {
                    return result(rows, "name", "content");
                  }
                  if (s.startsWith("insert")) {
                    return result(List.<Object[]>of(new Object[] {"new-key"}), "name");
                  }
                  return new MockResult[] {new MockResult(1)};
                }),
            SQLDialect.POSTGRES));
  }

  @SuppressWarnings("unchecked")
  private static MockResult[] result(List<Object[]> data, String... columns) {
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
