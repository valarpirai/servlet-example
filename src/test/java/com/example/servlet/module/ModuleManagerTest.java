package com.example.servlet.module;

import static org.junit.jupiter.api.Assertions.*;

import com.example.servlet.model.Module;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleManagerTest {

  @TempDir Path tempDir;

  private ModuleManager manager;

  @BeforeEach
  void setUp() throws Exception {
    resetSingleton();
    manager = ModuleManager.getInstance();
    // PropertiesUtil doesn't check system properties directly, so override the field
    java.lang.reflect.Field dirField = ModuleManager.class.getDeclaredField("modulesDirectory");
    dirField.setAccessible(true);
    dirField.set(manager, tempDir);
  }

  @AfterEach
  void tearDown() throws Exception {
    resetSingleton();
  }

  private void resetSingleton() throws Exception {
    java.lang.reflect.Field f = ModuleManager.class.getDeclaredField("instance");
    f.setAccessible(true);
    f.set(null, null);
  }

  // --- listModules ---

  @Test
  void listModules_emptyWhenNoModulesExist() throws IOException {
    assertTrue(manager.listModules().isEmpty());
  }

  @Test
  void listModules_returnsCreatedModules() throws IOException {
    manager.createModule("utils/math", "module.exports = {};");
    manager.createModule("utils/string", "module.exports = {};");
    List<Module> modules = manager.listModules();
    assertEquals(2, modules.size());
  }

  // --- createModule ---

  @Test
  void createModule_returnsModuleWithCorrectPath() throws IOException {
    Module m = manager.createModule("utils/math", "function add(a,b){return a+b;}");
    assertEquals("utils/math", m.getName());
  }

  @Test
  void createModule_storesContent() throws IOException {
    manager.createModule("helpers/fmt", "var x = 1;");
    Module loaded = manager.getModule("helpers/fmt");
    assertNotNull(loaded);
    assertTrue(loaded.getContent().contains("var x = 1;"));
  }

  @Test
  void createModule_throwsWhenAlreadyExists() throws IOException {
    manager.createModule("dup/module", "var a = 1;");
    assertThrows(IOException.class, () -> manager.createModule("dup/module", "var b = 2;"));
  }

  @Test
  void createModule_throwsOnNullPath() {
    assertThrows(IOException.class, () -> manager.createModule(null, "content"));
  }

  @Test
  void createModule_throwsOnEmptyPath() {
    assertThrows(IOException.class, () -> manager.createModule("  ", "content"));
  }

  @Test
  void createModule_throwsOnPathTraversal() {
    assertThrows(IOException.class, () -> manager.createModule("../escape", "content"));
  }

  @Test
  void createModule_throwsOnInvalidCharacters() {
    assertThrows(IOException.class, () -> manager.createModule("bad<name>", "content"));
  }

  @Test
  void createModule_throwsOnNullContent() {
    assertThrows(IOException.class, () -> manager.createModule("valid/path", null));
  }

  // --- getModule ---

  @Test
  void getModule_returnsNullForNonexistent() throws IOException {
    assertNull(manager.getModule("no/such/module"));
  }

  @Test
  void getModule_addsJsExtensionAutomatically() throws IOException {
    manager.createModule("ext/test", "var x=1;");
    assertNotNull(manager.getModule("ext/test"));
    assertNotNull(manager.getModule("ext/test.js"));
  }

  // --- updateModule ---

  @Test
  void updateModule_replacesContent() throws IOException {
    manager.createModule("upd/mod", "var old = 1;");
    manager.updateModule("upd/mod", "var new_ = 2;");
    Module m = manager.getModule("upd/mod");
    assertTrue(m.getContent().contains("new_"));
    assertFalse(m.getContent().contains("old"));
  }

  @Test
  void updateModule_throwsWhenModuleNotFound() {
    assertThrows(IOException.class, () -> manager.updateModule("nonexistent/mod", "content"));
  }

  // --- deleteModule ---

  @Test
  void deleteModule_removesModule() throws IOException {
    manager.createModule("del/mod", "var x=1;");
    assertNotNull(manager.getModule("del/mod"));
    manager.deleteModule("del/mod");
    assertNull(manager.getModule("del/mod"));
  }

  @Test
  void deleteModule_throwsWhenNotFound() {
    assertThrows(IOException.class, () -> manager.deleteModule("ghost/mod"));
  }

  @Test
  void deleteModule_cleansUpEmptyParentDirectory() throws IOException {
    manager.createModule("cleanup/nested/mod", "var x=1;");
    manager.deleteModule("cleanup/nested/mod");
    assertFalse(tempDir.resolve("cleanup/nested").toFile().exists());
  }

  // --- security ---

  @Test
  void resolveModulePath_rejectsTraversalAboveModulesDir() {
    assertThrows(SecurityException.class, () -> manager.getModule("../../etc/passwd"));
  }

  // --- getModulesDirectory ---

  @Test
  void getModulesDirectory_returnsTempDir() {
    assertEquals(tempDir, manager.getModulesDirectory());
  }
}
