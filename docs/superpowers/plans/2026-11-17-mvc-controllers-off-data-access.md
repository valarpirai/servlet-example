# Controllers Off Data Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No controller class depends on `TableRecord`, `Database` or jOOQ. The frozen baseline for `CONTROLLERS_AVOID_DATA_ACCESS` ends empty.

**Architecture:** Table reads move into two repositories in the Model layer: `RouteRepository` (web) and `ApiScriptRepository` (core). The health check and the table-API handler reach the database through small factories on classes that already own that work. `ScriptScopeBuilder` is script glue, so it moves from `processor` (Controller) to `script` (Service).

**Tech Stack:** Java 17, JUnit 5, Mockito, ArchUnit 1.3.0 (already in place), Maven.

**Spec:** `docs/superpowers/specs/2026-11-17-mvc-layering-design.md`, rollout step 2. Rules live in `docs/architecture/layers.md`.

## Global Constraints

- Read and write tables through `TableRecord` only. New classes extend nothing in jOOQ. (`.claude/rules/data-access.md`)
- Behaviour stays the same. These are moves, not rewrites.
- Handler and processor names stay registered in both `RouteRegistry` and `RouteDispatcher`. No handler is renamed here.
- Never edit an applied migration. This plan needs none.
- Every public method of `TableRecord` is a script API. Add none here.
- Run `mvn spotless:apply` before each commit. Commit messages end with the attribution line the session gives.
- Existing tests keep passing. Update callers when a constructor changes.

## Review Focus

- The database is not configured: routes keep their current list, the API-script lookup fails with the "not ready" error, the table API answers 503, health reports `DOWN` with status 503.
- A `routes` row that fails mapping is skipped. Rows after it still load.
- The route fingerprint is the same for an unchanged table and different after any column changes.
- A missing script name gives "not found", not an error.
- A repository built with a fixed `Database` (tests) and one built with `null` (uses `DbPropertiesLoader`) both work.

---

### Task 1: ApiScriptRepository

**Files:**
- Create: `core/src/main/java/com/example/repository/ApiScriptRepository.java`
- Modify: `core/src/main/java/com/example/processor/ApiScriptProcessor.java` (`loadEndpoint`, constructor)
- Modify: every caller of `new ApiScriptProcessor(` found by `grep -rn "new ApiScriptProcessor(" core web scheduler --include=*.java`
- Test: `core/src/test/java/com/example/repository/ApiScriptRepositoryTest.java`

**Interfaces:**
- Produces: `public class ApiScriptRepository` with `ApiScriptRepository(Database database)` (null means `DbPropertiesLoader.getDatabase()`) and `Optional<String> findContent(String name) throws Exception`. It throws `IOException("API scripts are stored in the database, which is not ready")` when no database is available.
- Produces: `ApiScriptProcessor(ApiScriptRepository repository)` replaces `ApiScriptProcessor(Database)`. The no-argument constructor builds `new ApiScriptRepository(null)`.

- [ ] **Step 1: Write the failing tests.** `ApiScriptRepositoryTest`, reusing the fake database the existing `ApiScriptProcessorTest` uses (`FakeScriptDb`): `findsContentByName` (row present, content returned), `missingNameIsEmpty` (`Optional.empty()`), `noDatabaseThrowsNotReady` (null database and `DbPropertiesLoader` unset, `IOException` with the message above).
- [ ] **Step 2: Run them and see them fail.** `mvn -q test -pl core -am -Dtest=ApiScriptRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: compile error, class missing.
- [ ] **Step 3: Implement `ApiScriptRepository`.** Move the body of `ApiScriptProcessor.loadEndpoint` here: `TableRecord` on `api_scripts`, `addQuery("name", name)`, `setLimit(1)`, `query()`.
- [ ] **Step 4: Switch `ApiScriptProcessor` to the repository.** Replace the `Database` field with an `ApiScriptRepository`. `loadEndpoint` becomes a call to `findContent(...).orElse(null)`. Update every caller from the grep.
- [ ] **Step 5: Run the module tests.** `mvn -q test -pl core -am`. Expected: PASS, including `ApiScriptProcessorTest`.
- [ ] **Step 6: Commit.** `git commit -m "refactor: read api_scripts through ApiScriptRepository"`.

### Task 2: RouteRepository

**Files:**
- Create: `web/src/main/java/com/example/repository/RouteRepository.java`
- Modify: `web/src/main/java/com/example/route/RouteRegistry.java`
- Modify: `web/src/test/java/com/example/route/TestRoutes.java`, `RouteRegistryTableTest.java`, and any other caller of `new RouteRegistry(` found by grep
- Test: `web/src/test/java/com/example/repository/RouteRepositoryTest.java`

**Interfaces:**
- Produces: `public class RouteRepository` with `RouteRepository(Database database)` (null means `DbPropertiesLoader.getDatabase()`), `boolean isAvailable()`, `String fingerprint() throws Exception` (the MD5 over every column of every row, ordered by `id`, as `RouteRegistry.readFingerprint` computes it today), and `List<Route> activeRoutes() throws Exception` (active rows ordered by `sort_order`, `id`, mapped as `RouteRegistry.toRoute` does today; a row that throws `RuntimeException` while mapping is logged and skipped).
- Produces: `RouteRegistry(RouteRepository repository)` and `RouteRegistry(RouteRepository repository, long refreshMillis)`, both package-private, replacing the two `Database` constructors. The private no-argument constructor builds `new RouteRepository(null)`. Validation (`validateRoute`) stays in `RouteRegistry`.

- [ ] **Step 1: Write the failing tests.** `RouteRepositoryTest`: `fingerprintIsStableForAnUnchangedTable`, `fingerprintChangesWhenAnyColumnChanges`, `activeRoutesSkipsInactiveRows`, `activeRoutesAreOrderedBySortOrderThenId`, `rowThatFailsMappingIsSkipped` (a row whose `methods` or `type` value makes the mapper throw, as the existing registry tests build), `isAvailableIsFalseWithoutADatabase`. Use the same test database setup as `RouteRegistryTableTest`.
- [ ] **Step 2: Run them and see them fail.** Expected: compile error, class missing.
- [ ] **Step 3: Implement `RouteRepository`.** Move `readFingerprint`, `loadFromTable`'s query loop, `toRoute`, `split` and the `database()` lookup out of `RouteRegistry`.
- [ ] **Step 4: Switch `RouteRegistry` to the repository.** Constructor and `load`/`refresh` call `repository.isAvailable()`, `fingerprint()` and `activeRoutes()`, then validate. Remove the `Database` and `TableRecord` imports.
- [ ] **Step 5: Update callers in tests.** Replace `new RouteRegistry(db)` with `new RouteRegistry(new RouteRepository(db))`, and the two-argument form the same way.
- [ ] **Step 6: Run the tests.** `mvn -q test -pl web -am -Dtest='Route*Test' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: PASS.
- [ ] **Step 7: Commit.** `git commit -m "refactor: read routes through RouteRepository"`.

### Task 3: Health check without Database

**Files:**
- Modify: `core/src/main/java/com/example/config/DbPropertiesLoader.java`
- Modify: `web/src/main/java/com/example/servlet/RouterServlet.java` (`handleHealth`)
- Test: `core/src/test/java/com/example/config/DbPropertiesLoaderTest.java` (create if absent)

**Interfaces:**
- Produces: `public static boolean isDatabaseUp()` on `DbPropertiesLoader`. It returns false when `getDatabase()` is null or `select 1` throws, and logs a warning with the message. It returns true otherwise.

- [ ] **Step 1: Write the failing test.** `isDatabaseUpIsFalseWhenNoDatabaseIsConfigured`: after `DbPropertiesLoader.close()`, `isDatabaseUp()` is false and does not throw.
- [ ] **Step 2: Run it and see it fail.** `mvn -q test -pl core -am -Dtest=DbPropertiesLoaderTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: compile error.
- [ ] **Step 3: Implement `isDatabaseUp`.** Run `getDatabase().query(dsl -> dsl.selectOne().fetch())` inside a try block. This is the code now in `RouterServlet.handleHealth`.
- [ ] **Step 4: Use it in `handleHealth`.** `status` is `UP` when `isDatabaseUp()` is true. Otherwise `DOWN` and `SC_SERVICE_UNAVAILABLE`. Drop the `Database` access and the catch block.
- [ ] **Step 5: Run the tests.** `mvn -q test -pl core -am` then `mvn -q test -pl web -am -Dtest='RouterServlet*Test' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: PASS.
- [ ] **Step 6: Commit.** `git commit -m "refactor: health check asks DbPropertiesLoader, not Database"`.

### Task 4: TableApiHandler without Database

**Files:**
- Modify: `web/src/main/java/com/example/tableapi/TableApi.java`, `TableEditor.java`
- Modify: `web/src/main/java/com/example/handler/TableApiHandler.java`
- Test: `web/src/test/java/com/example/tableapi/TableApiTest.java`, `TableEditorTest.java`

**Interfaces:**
- Produces: `static Optional<TableApi> forApplicationDatabase()` on `TableApi` and `static Optional<TableEditor> forApplicationDatabase()` on `TableEditor`. Each is empty when `DbPropertiesLoader.getDatabase()` is null. Existing `Database` constructors stay.

- [ ] **Step 1: Write the failing tests.** In each test class: `forApplicationDatabaseIsEmptyWithoutADatabase` (after `DbPropertiesLoader.close()`).
- [ ] **Step 2: Run them and see them fail.** Expected: compile error.
- [ ] **Step 3: Implement both factories.**
- [ ] **Step 4: Rewrite the handler's lookups.** Replace `database(response)` and `browser(response)` with calls to the factories. An empty result sends `503` with the existing message `Database not configured`. Remove the `Database` import.
- [ ] **Step 5: Run the tests.** `mvn -q test -pl web -am -Dtest='TableApi*Test,TableEditorTest' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: PASS.
- [ ] **Step 6: Commit.** `git commit -m "refactor: table API handler reaches data through TableApi and TableEditor"`.

### Task 5: Move ScriptScopeBuilder to the script package

**Files:**
- Move: `web/src/main/java/com/example/processor/ScriptScopeBuilder.java` to `web/src/main/java/com/example/script/ScriptScopeBuilder.java`
- Modify: every importer found by `grep -rn "ScriptScopeBuilder" web core --include=*.java --include=*.md`
- Modify: `docs/architecture/package-map.md`

- [ ] **Step 1: Move the file with `git mv`.** Change its `package` line to `com.example.script`.
- [ ] **Step 2: Fix imports and docs.** Update `ScriptProcessor` and every other reference the grep lists. Update the package-map entry: it leaves the `processor/` list and joins a `script/` list.
- [ ] **Step 3: Run the web suite.** `mvn -q test -pl web -am`. Expected: PASS.
- [ ] **Step 4: Commit.** `git commit -m "refactor: ScriptScopeBuilder is script glue, not a controller"`.

### Task 6: Shrink the baseline

**Files:**
- Modify: `web/src/test/resources/archunit-baseline/` (generated)
- Modify: `docs/architecture/layers.md` only if a statement in it is now false

- [ ] **Step 1: Run the rules.** `mvn -q test -pl web -am -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: PASS. The frozen store updates itself as violations disappear.
- [ ] **Step 2: Check the data-access baseline is empty.** The file named for `Controllers must not depend on TableRecord, Database or jOOQ` in `stored.rules` has zero violation lines. If any line remains, the task is not done: fix the code, not the baseline.
- [ ] **Step 3: Check the layer baseline did not grow.** Compare its line count with `git show HEAD:...` for the same file. It must be equal or smaller.
- [ ] **Step 4: Run the whole build.** `mvn clean verify`. Expected: BUILD SUCCESS.
- [ ] **Step 5: Commit.** `git add web/src/test/resources docs && git commit -m "test: controllers are off data access, baseline shrinks"`.
