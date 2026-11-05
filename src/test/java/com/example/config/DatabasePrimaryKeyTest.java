package com.example.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class DatabasePrimaryKeyTest {

  /** A database whose key lookup returns the given columns and counts the statements sent. */
  private static Database keyDb(List<String> columns, List<String> sent) {
    return new Database(null) {
      @Override
      public <T> T query(Work<T> fn) throws Exception {
        return fn.run(
            DSL.using(
                new MockConnection(
                    ctx -> {
                      sent.add(ctx.sql());
                      org.jooq.Field<String> column =
                          DSL.field(DSL.name("column_name"), String.class);
                      org.jooq.DSLContext create = DSL.using(SQLDialect.POSTGRES);
                      Result<org.jooq.Record1<String>> result = create.newResult(column);
                      for (String c : columns) {
                        result.add(create.newRecord(column).values(c));
                      }
                      return new MockResult[] {new MockResult(result.size(), result)};
                    }),
                SQLDialect.POSTGRES));
      }
    };
  }

  @Test
  void primaryKey_returnsSingleColumn() {
    assertEquals(Optional.of("id"), keyDb(List.of("id"), new ArrayList<>()).primaryKey("t"));
  }

  @Test
  void primaryKey_emptyForCompositeKey() {
    assertEquals(Optional.empty(), keyDb(List.of("a", "b"), new ArrayList<>()).primaryKey("t"));
  }

  @Test
  void primaryKey_emptyWhenNoKey() {
    assertEquals(Optional.empty(), keyDb(List.of(), new ArrayList<>()).primaryKey("t"));
  }

  @Test
  void primaryKey_queriesOncePerTable() {
    List<String> sent = new ArrayList<>();
    Database db = keyDb(List.of("id"), sent);
    db.primaryKey("t");
    db.primaryKey("t");
    assertEquals(1, sent.size());
  }
}
