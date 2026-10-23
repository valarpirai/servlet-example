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

  /** Rows a SELECT returns once a write has run. Null means the same rows as before. */
  private List<String[]> rowsAfterWrite;

  /** Business rules the lookup returns: name, condition, script. */
  private final List<String[]> rules = new ArrayList<>();

  private boolean written;

  private ModuleManager manager() {
    MockDataProvider provider =
        ctx -> {
          seen.add(ctx);
          String sql = ctx.sql().toLowerCase();
          var create = DSL.using(SQLDialect.POSTGRES);
          if (sql.startsWith("select") && sql.contains("business_rules")) {
            var result =
                create.newResult(
                    DSL.field("name", String.class),
                    DSL.field("condition", String.class),
                    DSL.field("script", String.class));
            for (String[] rule : rules) {
              result.add(
                  create
                      .newRecord(
                          DSL.field("name", String.class),
                          DSL.field("condition", String.class),
                          DSL.field("script", String.class))
                      .values(rule[0], rule[1], rule[2]));
            }
            return new MockResult[] {new MockResult(result.size(), result)};
          }
          if (sql.startsWith("select")) {
            var result = create.newResult(NAME, CONTENT, SIZE, CREATED, UPDATED);
            for (String[] row : written && rowsAfterWrite != null ? rowsAfterWrite : rows) {
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
          written = true;
          if (sql.startsWith("insert")) {
            var result = create.newResult(NAME);
            result.add(create.newRecord(NAME).values("made"));
            return new MockResult[] {new MockResult(1, result)};
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

  /** The first statement of this kind that the manager sent. */
  private MockExecuteContext statement(String prefix) {
    return seen.stream()
        .filter(c -> c.sql().toLowerCase().startsWith(prefix))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No " + prefix + " statement was sent"));
  }

  private boolean sent(String prefix) {
    return seen.stream().anyMatch(c -> c.sql().toLowerCase().startsWith(prefix));
  }

  private static final String[] STOP = {"stop", "true", "current.setAbortAction(true);"};

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
    rowsAfterWrite = List.<String[]>of(new String[] {"utils/math", "var a;"});
    Module m = manager().createModule("utils/math", "var a;");
    assertEquals("utils/math", m.getName());
    var insert = statement("insert");
    assertTrue(List.of(insert.bindings()).contains("utils/math"));
    assertTrue(List.of(insert.bindings()).contains("var a;"));
  }

  @Test
  void createModule_throwsWhenAlreadyExists() {
    rows.add(new String[] {"dup/module", "var a;"});
    assertThrows(IOException.class, () -> manager().createModule("dup/module", "var b = 2;"));
    assertFalse(sent("insert"));
  }

  @Test
  void createModule_isStoppedByABeforeRule() {
    rules.add(STOP);
    assertThrows(
        ModuleManager.BlockedException.class, () -> manager().createModule("new/mod", "var a;"));
    assertFalse(sent("insert"));
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
    rows.add(new String[] {"upd/mod", "var old = 1;"});
    rowsAfterWrite = List.<String[]>of(new String[] {"upd/mod", "var new_ = 2;"});
    Module m = manager().updateModule("upd/mod", "var new_ = 2;");
    assertTrue(List.of(statement("update").bindings()).contains("var new_ = 2;"));
    assertEquals("var new_ = 2;", m.getContent());
  }

  @Test
  void updateModule_throwsWhenModuleNotFound() {
    assertThrows(IOException.class, () -> manager().updateModule("nonexistent/mod", "content"));
    assertFalse(sent("update"));
  }

  @Test
  void updateModule_isStoppedByABeforeRule() {
    rows.add(new String[] {"upd/mod", "var old = 1;"});
    rules.add(STOP);
    assertThrows(
        ModuleManager.BlockedException.class, () -> manager().updateModule("upd/mod", "var x;"));
    assertFalse(sent("update"));
  }

  // --- deleteModule ---

  @Test
  void deleteModule_sendsDelete() throws IOException {
    rows.add(new String[] {"del/mod", "var a;"});
    manager().deleteModule("del/mod");
    assertTrue(List.of(statement("delete").bindings()).contains("del/mod"));
  }

  @Test
  void deleteModule_throwsWhenNotFound() {
    assertThrows(IOException.class, () -> manager().deleteModule("ghost/mod"));
    assertFalse(sent("delete"));
  }

  @Test
  void deleteModule_isStoppedByABeforeRule() {
    rows.add(new String[] {"del/mod", "var a;"});
    rules.add(STOP);
    assertThrows(ModuleManager.BlockedException.class, () -> manager().deleteModule("del/mod"));
    assertFalse(sent("delete"));
  }
}
