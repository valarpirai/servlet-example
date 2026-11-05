package com.example.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.Field;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class PropertyRepositoryTest {

  /** Answers every select with the given values in a {@code value} column. */
  private static class FakeDb extends Database {
    final List<String> sql = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    boolean fail;

    FakeDb() {
      super(null);
    }

    @Override
    public <T> T query(Work<T> fn) throws Exception {
      if (fail) {
        throw new RuntimeException("DB error");
      }
      return fn.run(
          DSL.using(
              new MockConnection(
                  ctx -> {
                    sql.add(ctx.sql().toLowerCase());
                    Field<String> value = DSL.field(DSL.name("value"), String.class);
                    org.jooq.DSLContext create = DSL.using(SQLDialect.POSTGRES);
                    Result<org.jooq.Record1<String>> result = create.newResult(value);
                    for (String v : values) {
                      result.add(create.newRecord(value).values(v));
                    }
                    return new MockResult[] {new MockResult(result.size(), result)};
                  }),
              SQLDialect.POSTGRES));
    }
  }

  private final FakeDb db = new FakeDb();
  private final PropertyRepository repo = new PropertyRepository(db);

  @Test
  void findValueByName_returnsValue_whenRowExists() throws Exception {
    db.values.add("8080");

    Optional<String> result = repo.findValueByName("server.port");

    assertEquals(Optional.of("8080"), result);
  }

  @Test
  void findValueByName_returnsEmpty_whenNoRow() throws Exception {
    assertFalse(repo.findValueByName("missing.key").isPresent());
  }

  @Test
  void findValueByName_returnsEmptyOptional_whenValueIsNull() throws Exception {
    db.values.add(null);

    assertFalse(repo.findValueByName("null.valued").isPresent());
  }

  @Test
  void findValueByName_filtersActiveRowsByName() throws Exception {
    repo.findValueByName("a.b");

    String select = db.sql.get(0);
    assertTrue(select.contains("app_properties"), select);
    assertTrue(select.contains("\"name\""), select);
    assertTrue(select.contains("\"active\""), select);
  }

  @Test
  void findValueByName_propagatesException() {
    db.fail = true;

    assertThrows(RuntimeException.class, () -> repo.findValueByName("any.key"));
  }
}
