package com.example.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.brandnew.Orphan;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.Location;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayersTest {

  @Test
  void everyClassIsInOneLayerOrOutsideLayers() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(new MainClassesOnly())
            .importPackages("com.example");
    assertTrue(
        classes.contain("com.example.common.util.JsonUtil")
            || classes.contain("com.example.util.JsonUtil"),
        "common module not imported");
    assertTrue(classes.contain("com.example.record.TableRecord"), "core module not imported");
    assertTrue(classes.contain("com.example.scheduler.Scheduler"), "scheduler module not imported");
    assertTrue(classes.contain("com.example.servlet.RouterServlet"), "web module not imported");
    List<String> unclassified =
        classes.stream()
            .filter(c -> Layers.layerOf(c).isEmpty() && !Layers.isOutsideLayers(c))
            .map(JavaClass::getName)
            .sorted()
            .toList();
    assertTrue(unclassified.isEmpty(), "Classes in no layer: " + unclassified);
  }

  @Test
  void unclassifiedPackageIsReported() {
    JavaClass orphan = new ClassFileImporter().importClass(Orphan.class);
    assertTrue(Layers.layerOf(orphan).isEmpty());
    assertFalse(Layers.isOutsideLayers(orphan));
  }

  @Test
  void mainClassesOnlySkipsTestJarsAndTestDirectories() {
    MainClassesOnly option = new MainClassesOnly();
    assertFalse(
        option.includes(
            Location.of(URI.create("jar:file:/m2/core-1.0-tests.jar!/com/example/A.class"))));
    assertFalse(
        option.includes(Location.of(URI.create("file:/w/core/target/test-classes/a/A.class"))));
    assertTrue(option.includes(Location.of(URI.create("jar:file:/m2/core-1.0.jar!/a/A.class"))));
  }
}
