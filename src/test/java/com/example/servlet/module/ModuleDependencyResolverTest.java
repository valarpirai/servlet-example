package com.example.servlet.module;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.servlet.model.Module;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ModuleDependencyResolverTest {

  @Mock private ModuleManager moduleManager;

  private ModuleDependencyResolver resolver;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resolver = new ModuleDependencyResolver(moduleManager);
  }

  @Test
  void testResolveImportsSimple() throws IOException {
    // Script imports moduleA
    String script = "import { foo } from 'moduleA';";

    // moduleA has no dependencies
    Module moduleA = new Module("moduleA", "moduleA", "export const foo = 1;");

    when(moduleManager.getModule("moduleA")).thenReturn(moduleA);

    List<String> resolved = resolver.resolveImports(script);

    assertEquals(1, resolved.size());
    assertEquals("moduleA", resolved.get(0));
  }

  @Test
  void testResolveImportsWithDependencies() throws IOException {
    // Script imports moduleA
    String script = "import { foo } from 'moduleA';";

    // moduleA imports moduleB
    Module moduleA = new Module("moduleA", "moduleA", "import { bar } from 'moduleB';");

    // moduleB has no dependencies
    Module moduleB = new Module("moduleB", "moduleB", "export const bar = 2;");

    when(moduleManager.getModule("moduleA")).thenReturn(moduleA);
    when(moduleManager.getModule("moduleB")).thenReturn(moduleB);

    List<String> resolved = resolver.resolveImports(script);

    assertEquals(2, resolved.size());
    // moduleB should come before moduleA (topological order)
    assertEquals("moduleB", resolved.get(0));
    assertEquals("moduleA", resolved.get(1));
  }

  @Test
  void testResolveImportsCommonJS() throws IOException {
    // Script uses require
    String script = "const foo = require('moduleA');";

    Module moduleA = new Module("moduleA", "moduleA", "module.exports = {};");

    when(moduleManager.getModule("moduleA")).thenReturn(moduleA);

    List<String> resolved = resolver.resolveImports(script);

    assertEquals(1, resolved.size());
    assertEquals("moduleA", resolved.get(0));
  }

  @Test
  void testResolveImportsMixedSyntax() throws IOException {
    // Script uses both import and require
    String script = "import { foo } from 'moduleA';\nconst bar = require('moduleB');";

    Module moduleA = new Module("moduleA", "moduleA", "export const foo = 1;");
    Module moduleB = new Module("moduleB", "moduleB", "module.exports = {};");

    when(moduleManager.getModule("moduleA")).thenReturn(moduleA);
    when(moduleManager.getModule("moduleB")).thenReturn(moduleB);

    List<String> resolved = resolver.resolveImports(script);

    assertEquals(2, resolved.size());
    assertTrue(resolved.contains("moduleA"));
    assertTrue(resolved.contains("moduleB"));
  }

  @Test
  void testCircularDependencyDetection() throws IOException {
    // moduleA imports moduleB
    String script = "import { foo } from 'moduleA';";

    // moduleA imports moduleB
    Module moduleA = new Module("moduleA", "moduleA", "import { bar } from 'moduleB';");

    // moduleB imports moduleA (circular!)
    Module moduleB = new Module("moduleB", "moduleB", "import { foo } from 'moduleA';");

    when(moduleManager.getModule("moduleA")).thenReturn(moduleA);
    when(moduleManager.getModule("moduleB")).thenReturn(moduleB);

    IOException exception = assertThrows(IOException.class, () -> resolver.resolveImports(script));

    assertTrue(exception.getMessage().contains("Circular dependency detected"));
  }

  @Test
  void testModuleNotFound() throws IOException {
    String script = "import { foo } from 'nonexistent';";

    when(moduleManager.getModule("nonexistent")).thenReturn(null);

    IOException exception = assertThrows(IOException.class, () -> resolver.resolveImports(script));

    assertTrue(exception.getMessage().contains("Module not found"));
  }

  @Test
  void testComplexDependencyGraph() throws IOException {
    // Script imports A and D
    String script = "import { a } from 'A';\nimport { d } from 'D';";

    // A imports B and C
    Module moduleA = new Module("A", "A", "import { b } from 'B';\nimport { c } from 'C';");

    // B imports C
    Module moduleB = new Module("B", "B", "import { c } from 'C';");

    // C has no dependencies
    Module moduleC = new Module("C", "C", "export const c = 3;");

    // D has no dependencies
    Module moduleD = new Module("D", "D", "export const d = 4;");

    when(moduleManager.getModule("A")).thenReturn(moduleA);
    when(moduleManager.getModule("B")).thenReturn(moduleB);
    when(moduleManager.getModule("C")).thenReturn(moduleC);
    when(moduleManager.getModule("D")).thenReturn(moduleD);

    List<String> resolved = resolver.resolveImports(script);

    assertEquals(4, resolved.size());

    // C must come before B and A
    int cIndex = resolved.indexOf("C");
    int bIndex = resolved.indexOf("B");
    int aIndex = resolved.indexOf("A");

    assertTrue(cIndex < bIndex);
    assertTrue(cIndex < aIndex);
    assertTrue(bIndex < aIndex);
  }

  @Test
  void testNoDuplicates() throws IOException {
    // Script imports both A and B, both import C
    String script = "import { a } from 'A';\nimport { b } from 'B';";

    Module moduleA = new Module("A", "A", "import { c } from 'C';");
    Module moduleB = new Module("B", "B", "import { c } from 'C';");
    Module moduleC = new Module("C", "C", "export const c = 3;");

    when(moduleManager.getModule("A")).thenReturn(moduleA);
    when(moduleManager.getModule("B")).thenReturn(moduleB);
    when(moduleManager.getModule("C")).thenReturn(moduleC);

    List<String> resolved = resolver.resolveImports(script);

    // C should appear only once
    assertEquals(3, resolved.size());
    assertEquals(1, resolved.stream().filter(m -> m.equals("C")).count());
  }

  @Test
  void testEmptyScript() throws IOException {
    String script = "";
    List<String> resolved = resolver.resolveImports(script);
    assertEquals(0, resolved.size());
  }

  @Test
  void testNoImports() throws IOException {
    String script = "const x = 1;\nconsole.log(x);";
    List<String> resolved = resolver.resolveImports(script);
    assertEquals(0, resolved.size());
  }

  @Test
  void testMultipleImportsFromSameModule() throws IOException {
    String script =
        "import { foo } from 'moduleA';\nimport { bar } from 'moduleA';\nconst baz ="
            + " require('moduleA');";

    Module moduleA = new Module("moduleA", "moduleA", "export const foo = 1;");

    when(moduleManager.getModule("moduleA")).thenReturn(moduleA);

    List<String> resolved = resolver.resolveImports(script);

    assertEquals(1, resolved.size());
    assertEquals("moduleA", resolved.get(0));
  }

  @Test
  void testSelfCircularDependency() throws IOException {
    String script = "import { foo } from 'moduleA';";

    // moduleA imports itself
    Module moduleA = new Module("moduleA", "moduleA", "import { foo } from 'moduleA';");

    when(moduleManager.getModule("moduleA")).thenReturn(moduleA);

    IOException exception = assertThrows(IOException.class, () -> resolver.resolveImports(script));

    assertTrue(exception.getMessage().contains("Circular dependency detected"));
  }
}
