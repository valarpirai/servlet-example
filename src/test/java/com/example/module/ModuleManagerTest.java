package com.example.module;

import static org.junit.jupiter.api.Assertions.*;

import com.example.config.Database;
import com.example.model.Module;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class ModuleManagerTest {

  private static final Field<String> NAME = DSL.field("name", String.class);
  private static final Field<String> CONTENT = DSL.field("content", String.class);
  private static final Field<Integer> SIZE = DSL.field("size_bytes", Integer.class);
  private static final Field<OffsetDateTime> CREATED =
      DSL.field("created_at", OffsetDateTime.class);
  private static final Field<OffsetDateTime> UPDATED =
      DSL.field("updated_at", OffsetDateTime.class);

  /** Statements the manager sent, in order. */
  private final List<MockExecuteContext> seen = new ArrayList<>();

  /** Rows a SELECT returns; empty by default. */
  private final List<String[]> rows = new ArrayList<>();

  private ModuleManager manager() {
    MockDataProvider provider =
        ctx -> {
          seen.add(ctx);
          String sql = ctx.sql().toLowerCase();
          var create = DSL.using(SQLDialect.POSTGRES);
          if (sql.startsWith("select")) {
            var result = create.newResult(NAME, CONTENT, SIZE, CREATED, UPDATED);
            for (String[] row : rows) {
              result.add(
                  create
                      .newRecord(NAME, CONTENT, SIZE, CREATED, UPDATED)
                      .values(
                          row[0],
                          row[1],
                          row[1].length(),
                          OffsetDateTime.now(),
                          OffsetDateTime.now()));
            }
            return new MockResult[] {new MockResult(result.size(), result)};
          }
          return new MockResult[] {new MockResult(1)};
        };
    Database db =
        new Database(null) {
          @Override
          public <T> T query(Work<T> fn) throws Exception {
            return fn.run(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));
          }
        };
    return new ModuleManager(db);
  }

  private Object lastBinding(int index) {
    return seen.get(seen.size() - 1).bindings()[index];
  }

  // --- listModules ---

  @Test
  void listModules_mapsRows() throws IOException {
    rows.add(new String[] {"utils/math", "var a;"});
    rows.add(new String[] {"utils/string", "var b;"});
    List<Module> modules = manager().listModules();
    assertEquals(2, modules.size());
    assertEquals("utils/math", modules.get(0).getName());
    assertEquals("utils/math.js", modules.get(0).getPath());
  }

  // --- getModule ---

  @Test
  void getModule_returnsNullForNonexistent() throws IOException {
    assertNull(manager().getModule("no/such/module"));
  }

  @Test
  void getModule_returnsContent() throws IOException {
    rows.add(new String[] {"helpers/fmt", "var x = 1;"});
    Module m = manager().getModule("helpers/fmt");
    assertEquals("var x = 1;", m.getContent());
    assertEquals(10, m.getSize());
  }

  @Test
  void getModule_stripsJsSuffixAndSlashes() throws IOException {
    manager().getModule("/ext/test.js");
    assertEquals("ext/test", lastBinding(0));
  }

  @Test
  void getModule_rejectsTraversal() {
    assertThrows(SecurityException.class, () -> manager().getModule("../../etc/passwd"));
  }
}
