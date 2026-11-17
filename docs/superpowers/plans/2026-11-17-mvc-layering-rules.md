# MVC Layering Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ArchUnit layer rules, a frozen baseline of today's violations, and a layer doc. No production code moves.

**Architecture:** One ArchUnit test class in `web` checks the Controller, Service, Model and View layers across all modules. A `Layers` helper maps packages to layers. `FreezingArchRule` stores existing violations in a baseline file, so only new violations fail.

**Tech Stack:** Java 17 bytecode, JUnit 5, ArchUnit 1.3.0, Maven.

**Spec:** `docs/superpowers/specs/2026-11-17-mvc-layering-design.md` (rollout step 1 only).

## Global Constraints

- Pin the ArchUnit version in `shared-build/catalog/thirdparty/pom.xml`. Declare it version-free in `web/pom.xml`.
- No production code changes. Step 1 lists violations and fixes none.
- Tests live in `web`, in package `com.example.architecture`.
- Docs: kebab-case, 20-250 lines, Hemingway style, a "Related" section, linked from `docs/readme.md`.
- Run `mvn spotless:apply` before each commit.
- Commit messages end with the attribution line the session gives.

## Review Focus

- A new package under `com.example` with no layer: the classification test fails.
- A controller that touches `TableRecord`: the data-access rule reports it.
- The baseline file deleted: the build fails, it does not silently regenerate.
- The View layer has no classes yet: no "empty layer" error.
- Test classes and fixtures: never counted in the main analysis.

---

### Task 1: ArchUnit dependency and layer classification

**Files:**
- Modify: `shared-build/catalog/thirdparty/pom.xml`
- Modify: `web/pom.xml`
- Create: `web/src/test/java/com/example/architecture/Layers.java`
- Test: `web/src/test/java/com/example/architecture/LayersTest.java`

**Interfaces:**
- Produces: `final class Layers` with `static final String CONTROLLER, SERVICE, MODEL, VIEW`, `static final String[] CONTROLLER_PACKAGES, SERVICE_PACKAGES, MODEL_PACKAGES, VIEW_PACKAGES` (ArchUnit globs such as `com.example.handler..`), `static Optional<String> layerOf(JavaClass c)`, and `static boolean isOutsideLayers(JavaClass c)`.

- [ ] **Step 1: Add the dependency.** Add property `<archunit.version>1.3.0</archunit.version>` and a managed `com.tngtech.archunit:archunit-junit5` entry to the thirdparty catalog. Declare it in `web/pom.xml` with `<scope>test</scope>` and no version.

- [ ] **Step 2: Write the failing test.** In `LayersTest`, import `com.example` main classes with `ClassFileImporter` and `ImportOption.DoNotIncludeTests`.
  - `everyClassIsInOneLayerOrOutsideLayers`: for each imported class, `Layers.layerOf(c).isPresent() || Layers.isOutsideLayers(c)` holds. The failure message names the class.
  - `unclassifiedPackageIsReported`: `Layers.layerOf` returns empty for a class in `com.example.brandnew`, and `isOutsideLayers` returns false for it. Build the class with `new ClassFileImporter().importClass(...)` on a small test fixture `com.example.brandnew.Orphan` under `web/src/test/java`.

- [ ] **Step 3: Run it and see it fail.** Run: `mvn -q test -pl web -am -Dtest=LayersTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: compile error, `Layers` does not exist.

- [ ] **Step 4: Implement `Layers`.** Package-to-layer map:
  - Controller: `handler`, `processor`, `route`, `servlet`.
  - Service: `tableapi`, `storage`, `scheduler`, `datasource`, `extlib`, `mongo`, `script`, `module`.
  - Model: `record`, `repository`, `config`, `model`.
  - View: none yet. Declare the empty array.
  - Outside layers: `com.example.util..`, `com.example.exception..`, `com.example.security..`, and the root class `com.example.Main`.
  - Match with `JavaClass.getPackageName()` and prefix checks, not exact equality.

- [ ] **Step 5: Run it and see it pass.** Same command. Expected: both tests PASS. If the first test names a class, add its package to the right layer and say so in the commit body.

- [ ] **Step 6: Commit.** `git add shared-build web && git commit -m "test: classify packages into MVC layers"`.

### Task 2: Layer rules with a frozen baseline

**Files:**
- Create: `web/src/test/java/com/example/architecture/ArchitectureTest.java`
- Create: `web/src/test/java/com/example/handler/ViolatingFixture.java`
- Create: `web/src/test/resources/archunit.properties`
- Create: `web/src/test/resources/archunit-baseline/` (generated)

**Interfaces:**
- Consumes: `Layers` constants and packages from Task 1.
- Produces: rule fields `LAYERS` and `CONTROLLERS_AVOID_DATA_ACCESS`, both `ArchRule`, both wrapped in `FreezingArchRule.freeze(...)`.

- [ ] **Step 1: Write the failing test.** `ArchitectureTest` is annotated `@AnalyzeClasses(packages = "com.example", importOptions = ImportOption.DoNotIncludeTests.class)`. It has:
  - `LAYERS` rule: `layeredArchitecture().consideringOnlyDependenciesInLayers().withOptionalLayers(true)`, four layers from `Layers`, then `whereLayer(CONTROLLER).mayNotBeAccessedByAnyLayer()`, `whereLayer(SERVICE).mayOnlyBeAccessedByLayers(CONTROLLER, SERVICE)`, `whereLayer(VIEW).mayOnlyBeAccessedByLayers(CONTROLLER)`, `whereLayer(VIEW).mayOnlyAccessLayers(MODEL)`, `whereLayer(MODEL).mayOnlyAccessLayers(MODEL)`.
  - `CONTROLLERS_AVOID_DATA_ACCESS` rule: no class in the Controller packages depends on `com.example.record..`, `org.jooq..` or `com.example.config.Database`.
  - Each rule is a `@ArchTest static final ArchRule` wrapped in `FreezingArchRule.freeze(...)`.
  - A plain JUnit test `dataAccessRuleCatchesAController`: import only `ViolatingFixture` with `ClassFileImporter`, evaluate the unfrozen data-access rule, and assert `result.hasViolation()`.

- [ ] **Step 2: Add the fixture.** `com.example.handler.ViolatingFixture` holds a field of type `com.example.record.TableRecord`. Test sources only.

- [ ] **Step 3: Configure freezing.** In `archunit.properties` set `freeze.store.default.path=src/test/resources/archunit-baseline` and `freeze.store.default.allowStoreCreation=true`. Surefire runs from `web/`, so the path is relative to that module.

- [ ] **Step 4: Generate the baseline.** Run: `mvn -q test -pl web -am -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: PASS, and `archunit-baseline/` now holds a stored-rules file plus one file per rule. Read the stored violations. Confirm they name `TableApiHandler`, `DataBrowserHandler` and similar, and name no class in `util`.

- [ ] **Step 5: Lock the store.** Set `freeze.store.default.allowStoreCreation=false` in `archunit.properties`. Keep `allowStoreUpdate` at its default, so fixed violations drop out of the baseline.

- [ ] **Step 6: Prove a missing baseline fails.** Move `archunit-baseline/` aside and run the same command. Expected: FAIL, no store creation. Move it back and rerun. Expected: PASS.

- [ ] **Step 7: Prove a new violation fails.** Temporarily add `import com.example.record.TableRecord;` plus a field to `route/RouteDispatcher.java`... no: pick a controller class that does not use `TableRecord` today, such as `handler/SchedulerHandler.java`. Run the command. Expected: FAIL naming that class. Revert the change with `git checkout -- <file>`.

- [ ] **Step 8: Run the whole `web` suite.** `mvn -q test -pl web -am`. Expected: PASS.

- [ ] **Step 9: Commit.** `git add web && git commit -m "test: enforce MVC layer rules with a frozen baseline"`.

### Task 3: Layer doc and index links

**Files:**
- Create: `docs/architecture/layers.md`
- Modify: `docs/readme.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Write `docs/architecture/layers.md`.** Cover: the four layers and their packages (a table), the dependency rules, what View means in this API, where the tests live, how to read and shrink the baseline, and how to classify a new package in `Layers`. State the current state only. Read `.claude/rules/documentation.md` first. Name `Layers` and `ArchitectureTest` by file path, no line numbers. End with a "Related" section.

- [ ] **Step 2: Link it.** Add one line to the architecture section of `docs/readme.md`. Add a row to the "Where to Look" table in `CLAUDE.md`: task "Layering", doc `docs/architecture/layers.md`, key files `web/src/test/java/com/example/architecture/`.

- [ ] **Step 3: Check the doc.** Run `wc -l docs/architecture/layers.md`. Expected: 20 to 250 lines. Re-read each claim against `Layers.java`.

- [ ] **Step 4: Commit.** `git add docs CLAUDE.md && git commit -m "docs: describe the MVC layers and how they are enforced"`. Include the design and plan files in this commit.

- [ ] **Step 5: Final check.** `mvn clean verify`. Expected: BUILD SUCCESS, including Spotless and the enforcer.
