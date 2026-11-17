package com.example.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Guards the frozen baseline itself. A frozen rule is stored under its description. If the
 * description changes, ArchUnit stores every current violation as a new baseline and the build
 * stays green. These tests make that loud.
 */
class BaselineGuardTest {

  private static final Path BASELINE = Path.of("src/test/resources/archunit-baseline");
  private static final Path PROPERTIES = Path.of("src/test/resources/archunit.properties");

  @Test
  void layerRuleHasAStableName() {
    assertEquals("MVC layer dependencies", ArchitectureTest.layerDependencies().getDescription());
  }

  @Test
  void storedRulesHoldExactlyTheFrozenRules() throws IOException {
    Properties stored = load(BASELINE.resolve("stored.rules"));

    assertEquals(
        Set.of(
            ArchitectureTest.controllersAvoidDataAccess().getDescription(),
            ArchitectureTest.sharedStaysIndependent().getDescription(),
            ArchitectureTest.layerDependencies().getDescription()),
        stored.stringPropertyNames());
  }

  @Test
  void propertiesLockTheStore() throws IOException {
    Properties properties = load(PROPERTIES);

    assertEquals("false", properties.getProperty("freeze.store.default.allowStoreCreation"));
    assertEquals("false", properties.getProperty("freeze.store.default.allowStoreUpdate"));
  }

  private static Properties load(Path path) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = Files.newInputStream(path)) {
      properties.load(in);
    }
    return properties;
  }
}
