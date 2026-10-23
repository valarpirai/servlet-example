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

  /** Row count reported for INSERT, UPDATE and DELETE. */
  private int affected = 1;

  private ModuleManager manager() {
    MockDataProvider provider =
        ctx -> {
          seen.add(ctx);
          if (ctx.sql().toLowerCase().startsWith("select")) {
            var result =
                DSL.using(SQLDialect.POSTGRES).newResult(NAME, CONTENT, SIZE, CREATED, UPDATED);
            for (String[] row : rows) {
              result.add(
                  DSL.using(SQLDialect.POSTGRES)
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
          return new MockResult[] {new MockResult(affected)};
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

  private String lastSql() {
    return seen.get(seen.size() - 1).sql().toLowerCase();
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

  // --- createModule ---

  @Test
  void createModule_insertsNameAndContent() throws IOException {
    rows.add(new String[] {"utils/math", "var a;"});
    Module m = manager().createModule("utils/math", "var a;");
    assertEquals("utils/math", m.getName());
    Object[] insert = seen.get(0).bindings();
    assertEquals("utils/math", insert[0]);
    assertEquals("var a;", insert[1]);
    assertTrue(seen.get(0).sql().toLowerCase().contains("on conflict"));
  }

  @Test
  void createModule_throwsWhenAlreadyExists() {
    affected = 0;
    assertThrows(IOException.class, () -> manager().createModule("dup/module", "var b = 2;"));
  }

  @Test
  void createModule_throwsOnNullPath() {
    assertThrows(IOException.class, () -> manager().createModule(null, "content"));
  }

  @Test
  void createModule_throwsOnEmptyPath() {
    assertThrows(IOException.class, () -> manager().createModule("  ", "content"));
  }

  @Test
  void createModule_throwsOnPathTraversal() {
    assertThrows(IOException.class, () -> manager().createModule("../escape", "content"));
  }

  @Test
  void createModule_throwsOnInvalidCharacters() {
    assertThrows(IOException.class, () -> manager().createModule("bad<name>", "content"));
  }

  @Test
  void createModule_throwsOnNullContent() {
    assertThrows(IOException.class, () -> manager().createModule("valid/path", null));
  }

  @Test
  void createModule_neverTouchesDbWhenInvalid() {
    assertThrows(IOException.class, () -> manager().createModule("../escape", "content"));
    assertTrue(seen.isEmpty());
  }

  // --- updateModule ---

  @Test
  void updateModule_sendsUpdate() throws IOException {
    rows.add(new String[] {"upd/mod", "var new_ = 2;"});
    Module m = manager().updateModule("upd/mod", "var new_ = 2;");
    assertTrue(seen.get(0).sql().toLowerCase().startsWith("update"));
    assertEquals("var new_ = 2;", m.getContent());
  }

  @Test
  void updateModule_throwsWhenModuleNotFound() {
    affected = 0;
    assertThrows(IOException.class, () -> manager().updateModule("nonexistent/mod", "content"));
  }

  // --- deleteModule ---

  @Test
  void deleteModule_sendsDelete() throws IOException {
    manager().deleteModule("del/mod");
    assertTrue(lastSql().startsWith("delete"));
    assertEquals("del/mod", lastBinding(0));
  }

  @Test
  void deleteModule_throwsWhenNotFound() {
    affected = 0;
    assertThrows(IOException.class, () -> manager().deleteModule("ghost/mod"));
  }
}
