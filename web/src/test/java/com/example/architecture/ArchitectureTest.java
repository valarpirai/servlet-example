package com.example.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.handler.ViolatingFixture;
import com.example.handler.ViolatingJdbcFixture;
import com.example.handler.ViolatingRepositoryFixture;
import com.example.util.ViolatingUtilFixture;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * MVC layer rules for every module. Existing violations sit in a frozen baseline under {@code
 * src/test/resources/archunit-baseline}. A new violation fails the build.
 */
@AnalyzeClasses(packages = "com.example", importOptions = MainClassesOnly.class)
class ArchitectureTest {

  /**
   * Builds the rule that keeps controllers away from data access.
   *
   * @return the unfrozen rule
   */
  static ArchRule controllersAvoidDataAccess() {
    DescribedPredicate<JavaClass> database =
        DescribedPredicate.describe(
            "com.example.config.Database", c -> c.getName().equals("com.example.config.Database"));
    return noClasses()
        .that()
        .resideInAnyPackage(Layers.CONTROLLER_PACKAGES)
        .should()
        .dependOnClassesThat(
            resideInAnyPackage(
                    "com.example.record..",
                    "com.example.repository..",
                    "java.sql..",
                    "javax.sql..",
                    "org.jooq..")
                .or(database))
        .as("Controllers must not depend on TableRecord, Database, repositories, JDBC or jOOQ");
  }

  /**
   * Builds the rule that keeps shared code from laundering access to a higher layer.
   *
   * @return the unfrozen rule
   */
  static ArchRule sharedStaysIndependent() {
    String[] forbidden =
        Stream.of(
                Layers.CONTROLLER_PACKAGES,
                Layers.SERVICE_PACKAGES,
                new String[] {"com.example.record..", "com.example.repository..", "org.jooq.."})
            .flatMap(Arrays::stream)
            .toArray(String[]::new);
    return noClasses()
        .that()
        .resideInAnyPackage(
            "com.example.util..", "com.example.exception..", "com.example.security..")
        .should()
        .dependOnClassesThat(resideInAnyPackage(forbidden))
        .as("Shared packages must not depend on controllers, services or data access");
  }

  /**
   * Builds the rule that fixes which layer may call which.
   *
   * @return the unfrozen rule
   */
  static ArchRule layerDependencies() {
    return layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .withOptionalLayers(true)
        .layer(Layers.CONTROLLER)
        .definedBy(Layers.CONTROLLER_PACKAGES)
        .layer(Layers.SERVICE)
        .definedBy(Layers.SERVICE_PACKAGES)
        .layer(Layers.MODEL)
        .definedBy(Layers.MODEL_PACKAGES)
        .layer(Layers.VIEW)
        .definedBy(Layers.VIEW_PACKAGES)
        .whereLayer(Layers.CONTROLLER)
        .mayNotBeAccessedByAnyLayer()
        .whereLayer(Layers.SERVICE)
        .mayOnlyBeAccessedByLayers(Layers.CONTROLLER, Layers.SERVICE)
        .whereLayer(Layers.VIEW)
        .mayOnlyBeAccessedByLayers(Layers.CONTROLLER)
        .whereLayer(Layers.VIEW)
        .mayOnlyAccessLayers(Layers.MODEL)
        .whereLayer(Layers.MODEL)
        .mayOnlyAccessLayers(Layers.MODEL)
        .as("MVC layer dependencies");
  }

  @ArchTest
  static final ArchRule CONTROLLERS_AVOID_DATA_ACCESS = freeze(controllersAvoidDataAccess());

  @ArchTest static final ArchRule SHARED_STAYS_INDEPENDENT = freeze(sharedStaysIndependent());

  @ArchTest static final ArchRule LAYERS = freeze(layerDependencies());

  @Test
  void dataAccessRuleCatchesAController() {
    var classes = new ClassFileImporter().importClasses(ViolatingFixture.class);
    assertTrue(controllersAvoidDataAccess().evaluate(classes).hasViolation());
  }

  @Test
  void dataAccessRuleCatchesARepository() {
    var classes = new ClassFileImporter().importClasses(ViolatingRepositoryFixture.class);
    assertTrue(controllersAvoidDataAccess().evaluate(classes).hasViolation());
  }

  @Test
  void dataAccessRuleCatchesRawJdbc() {
    var classes = new ClassFileImporter().importClasses(ViolatingJdbcFixture.class);
    assertTrue(controllersAvoidDataAccess().evaluate(classes).hasViolation());
  }

  @Test
  void sharedRuleCatchesAUtilThatReachesIntoDataAccess() {
    var classes = new ClassFileImporter().importClasses(ViolatingUtilFixture.class);
    assertTrue(sharedStaysIndependent().evaluate(classes).hasViolation());
  }
}
