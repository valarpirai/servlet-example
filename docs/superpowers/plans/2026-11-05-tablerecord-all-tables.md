# TableRecord on All Tables Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `TableRecord` works on any table when `record.allowedTables` is unset, the setting moves out of `app_properties`, and four direct jOOQ readers move onto `TableRecord`.

**Architecture:** An `AllowedTables` parser reads the setting from `application.yml` only. `Database` finds and caches a table's primary key. `TableRecord` asks for the key lazily, so reads never need one. The four callers are read-only, so they need no key.

**Tech Stack:** Java 17+, jOOQ, JUnit 5, Mockito, Flyway, PostgreSQL.

**Spec:** [2026-11-05-tablerecord-all-tables-design.md](../specs/2026-11-05-tablerecord-all-tables-design.md)

## Global Constraints

- New code reads and writes tables through `TableRecord`. jOOQ stays only in `TableRecord`, `Database`, `BusinessRuleRunner`, `DatabaseAttachmentStorage` and `TableApi`.
- Unset or blank `record.allowedTables` means every table is allowed.
- The setting comes from `application.yml` (`${RECORD_ALLOWED_TABLES:}`), never from `app_properties`.
- Names keep the `IDENTIFIER` check in `TableRecord`.
- Never edit an applied Flyway migration. Add a new `V{n}__` file.
- Every public `TableRecord` method is a script API. Document and test each new one.
- `TableRecord` stays the plain data layer. A later `TableRecordSecure` wrapper will add ACL checks by delegating to it. So: no ACL logic here, no `final` on the class or its public methods, and every new public method is plain delegation-friendly (no static state the wrapper cannot reach). `record.allowedTables` is a coarse deployment restriction, separate from ACLs.
- Java comments use JSDoc style with `@param` and `@return`, and no HTML tags.
- Run `mvn spotless:apply` before each commit. Commit messages end with the attribution line from the session reminder.

## Review Focus

- A table name with quotes or spaces: rejected before any SQL runs (Task 3).
- A table with no primary key or a composite one: reads work, every write throws a message naming the table (Task 3).
- Setting `a, b:key,,` with spaces and empty entries: parsed, empty entries skipped (Task 1).
- Setting present, table not listed: constructor throws `IllegalArgumentException` (Task 3).
- `app_properties` unreachable during `PropertiesUtil.getString`: falls back to YAML, no recursion (Task 5).

---

### Task 1: Allow-list from the file

**Files:**
- Create: `src/main/java/com/example/record/AllowedTables.java`
- Modify: `src/main/java/com/example/util/PropertiesUtil.java`, `src/main/resources/application.yml`
- Test: `src/test/java/com/example/record/AllowedTablesTest.java`

**Interfaces:**
- Produces: `public static String PropertiesUtil.getFileString(String key, String defaultValue)` reads YAML only, placeholders resolved, never the DB.
- Produces: `AllowedTables.parse(String setting)` returns `AllowedTables`. `boolean permits(String table)`. `Optional<String> keyFor(String table)` is the listed key, empty when none is listed. `static AllowedTables current()` parses `getFileString("record.allowedTables", "")` once and caches it. `static void reset()` clears the cache, for tests.

- [ ] **Step 1: Write the failing tests** in `AllowedTablesTest`: `blankPermitsEverything` (`parse("")`, `parse(null)`, `parse("  ")` permit `"anything"`); `listedTablesOnly` (`parse("a, b:id")` permits `a` and `b`, not `c`); `keyFromPair` (`keyFor("b")` is `Optional.of("id")`, `keyFor("a")` is empty); `emptyEntriesSkipped` (`parse("a,,")` permits `a`, not `""`).
- [ ] **Step 2: Run** `mvn test -Dtest=AllowedTablesTest`. Expected: compile failure, class missing.
- [ ] **Step 3: Implement `AllowedTables`** as above. Split on commas, trim, split each entry on the first colon. No logging.
- [ ] **Step 4: Add `getFileString`** next to `getString`. It calls the private `getYamlProperty` and returns `defaultValue` when null.
- [ ] **Step 5: Add to `application.yml`**: `record:` with `allowedTables: ${RECORD_ALLOWED_TABLES:}`. Update the file's header comment: db credentials plus this one setting.
- [ ] **Step 6: Add a test** in `PropertiesUtilTest` (find the existing one): `getFileString` ignores a value set through the DB repository.
- [ ] **Step 7: Run** `mvn test -Dtest='AllowedTablesTest,PropertiesUtilTest'`. Expected: PASS.
- [ ] **Step 8: Commit** `feat: read record.allowedTables from the config file`.

### Task 2: Primary key discovery in Database

**Files:**
- Modify: `src/main/java/com/example/config/Database.java`
- Test: `src/test/java/com/example/config/DatabaseTest.java`

**Interfaces:**
- Produces: `public Optional<String> Database.primaryKey(String table)`. Empty when the table has no primary key or a composite one. Cached per `Database` instance in a `ConcurrentHashMap`. Failed lookups are not cached.

- [ ] **Step 1: Write failing tests** in `DatabaseTest`, using a `Database` subclass that overrides `query` with a jOOQ `MockConnection` (see `MockDb` for the pattern): `primaryKey_returnsSingleColumn`, `primaryKey_emptyForCompositeKey` (two rows), `primaryKey_emptyWhenNoKey` (zero rows), `primaryKey_queriesOncePerTable` (count statements after two calls is 1).
- [ ] **Step 2: Run** `mvn test -Dtest=DatabaseTest`. Expected: FAIL, method missing.
- [ ] **Step 3: Implement `primaryKey`**. Query `information_schema.table_constraints` joined to `key_column_usage` on constraint name and schema, with `constraint_type = 'PRIMARY KEY'`, `table_schema = current_schema()` and the table name bound as a parameter. One row means a key.
- [ ] **Step 4: Run** `mvn test -Dtest=DatabaseTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: Database finds a table's primary key`.

### Task 3: TableRecord uses both

**Files:**
- Modify: `src/main/java/com/example/record/TableRecord.java`, `src/test/java/com/example/record/MockDb.java`
- Test: `src/test/java/com/example/record/TableRecordTest.java`, `TableRecordBulkTest.java`

**Interfaces:**
- Consumes: `AllowedTables.current()`, `Database.primaryKey(String)`.
- Produces: `public List<String> TableRecord.getColumnNames()`, the column names of the current row in query order. Empty before the first `next()`.
- Removes: `TableRecord.keyColumnOf(String)` and `allowedKey`. Grep for other callers first and update them.

- [ ] **Step 1: Extend `MockDb`** so a statement containing `information_schema` returns one row with the key column set by a new method `void key(String column)`, default `name`. Existing tests keep passing.
- [ ] **Step 2: Write failing tests** in `TableRecordTest`: `anyTableWorksWhenSettingUnset`; `unlistedTableRejectedWhenSettingSet` (call `AllowedTables.reset()`, set the YAML value through a test hook, expect `IllegalArgumentException` containing the table name); `badNameRejected` (`new TableRecord(db, "a\"b")` throws before any SQL); `readsNeedNoKey` (no `information_schema` statement after `query()`); `writeWithoutKeyThrows` (MockDb with no key rows, `insert`, `update`, `deleteRecord`, `updateMultiple`, `deleteMultiple` each throw `IllegalStateException` whose message contains the table name); `getColumnNames_listsRowColumns`.
- [ ] **Step 3: Run** `mvn test -Dtest='TableRecordTest,TableRecordBulkTest'`. Expected: FAIL.
- [ ] **Step 4: Change the constructor**: check `AllowedTables.current().permits(table)` and the `IDENTIFIER` pattern. Replace the `keyColumn` field with `private String key()`: the listed key from `AllowedTables`, else `database.primaryKey(table)`, else throw `IllegalStateException("Table " + table + " has no single-column primary key; it is read-only")`. Cache the result in a field. Replace every `keyColumn` use with `key()`.
- [ ] **Step 5: Add `getColumnNames`** and its Javadoc.
- [ ] **Step 6: Run** `mvn test`. Expected: PASS for record tests; fix other tests only if they relied on `js_modules:name`.
- [ ] **Step 7: Commit** `feat: TableRecord works on any table`.

### Task 4: Remove the old setting

**Files:**
- Create: `src/main/resources/db/V{n}__drop_allowed_tables_property.sql`, where `{n}` is the next unused version. Check with `ls src/main/resources/db`.

- [ ] **Step 1: Write the migration**: `DELETE FROM app_properties WHERE name = 'record.allowedTables';` with a comment saying the setting now lives in `application.yml`.
- [ ] **Step 2: Run** `mvn test`. Expected: PASS. If a migration test lists versions, update it.
- [ ] **Step 3: Commit** `feat: drop record.allowedTables from app_properties`.

### Task 5: PropertyRepository on TableRecord

**Files:**
- Modify: `src/main/java/com/example/config/PropertyRepository.java`, `src/test/java/com/example/config/PropertyRepositoryTest.java`

**Interfaces:**
- Consumes: `new TableRecord(Database, "app_properties")`, `addQuery`, `query`, `next`, `getValue`.
- Produces: `Optional<String> findValueByName(String name) throws Exception` keeps its signature. A null `value` column gives `Optional.empty()`.

- [ ] **Step 1: Rewrite the three tests** to fake the database with a `Database` subclass that answers `select` on `app_properties` (pattern: `MockDb`, `FakeScriptDb`). Keep names: `findValueByName_returnsValue_whenRowExists`, `_returnsEmpty_whenNoRow`, `_returnsEmptyOptional_whenValueIsNull`. Add `findValueByName_filtersActiveRows`: the captured SQL contains both `name` and `active`.
- [ ] **Step 2: Run** `mvn test -Dtest=PropertyRepositoryTest`. Expected: FAIL.
- [ ] **Step 3: Replace the jOOQ body** with a `TableRecord` query on `name` and `active = true`, limit 1. Remove the jOOQ imports.
- [ ] **Step 4: Add a test** `PropertiesUtilTest.fallsBackToYamlWhenDatabaseThrows`: a repository whose `findValueByName` throws, `getString` returns the YAML value and does not recurse.
- [ ] **Step 5: Run** `mvn test -Dtest='PropertyRepositoryTest,PropertiesUtilTest'`. Expected: PASS.
- [ ] **Step 6: Boot check** (needs PostgreSQL): run `mvn -PappRun`, then `curl localhost:8080/health`. Expected: healthy, no `StackOverflowError` in `logs/`.
- [ ] **Step 7: Commit** `refactor: PropertyRepository reads through TableRecord`.

### Task 6: ModuleManager on TableRecord

**Files:**
- Modify: `src/main/java/com/example/module/ModuleManager.java`, `src/test/java/com/example/module/ModuleManagerTest.java`

**Interfaces:**
- Consumes: `TableRecord` on `js_modules`. Columns `created_at` and `updated_at` come back as ISO text; parse with `OffsetDateTime.parse(text).toInstant()`.
- Produces: `listModules()` and `getModule(String)` keep their signatures. `listModules` orders by `name`.

- [ ] **Step 1: Adapt the inline mock provider** in `ModuleManagerTest` only if its SQL matching breaks. Existing assertions stay.
- [ ] **Step 2: Run** `mvn test -Dtest=ModuleManagerTest`. Expected: PASS before the change (baseline).
- [ ] **Step 3: Replace** the jOOQ calls and the `run` helper's `dsl` use with `TableRecord`. Keep the "database not ready" `IOException`. Remove jOOQ imports.
- [ ] **Step 4: Run** `mvn test -Dtest=ModuleManagerTest`. Expected: PASS.
- [ ] **Step 5: Commit** `refactor: ModuleManager reads through TableRecord`.

### Task 7: ApiScriptProcessor on TableRecord

**Files:**
- Modify: `src/main/java/com/example/processor/ApiScriptProcessor.java`, `src/test/java/com/example/processor/FakeScriptDb.java` if its SQL matching breaks
- Test: `ApiScriptProcessorTest`

**Interfaces:**
- Produces: `private String loadEndpoint(String endpointName) throws Exception` returns the `content` of the `api_scripts` row, or null.

- [ ] **Step 1: Run** `mvn test -Dtest='ApiScriptProcessorTest,ScriptProcessorSecurityTest'`. Expected: PASS (baseline).
- [ ] **Step 2: Replace** the body of `loadEndpoint` with a `TableRecord` query on `name`, limit 1. Remove unused jOOQ imports and constants.
- [ ] **Step 3: Run** the same tests. Expected: PASS.
- [ ] **Step 4: Commit** `refactor: ApiScriptProcessor reads through TableRecord`.

### Task 8: RouteRegistry on TableRecord

**Files:**
- Modify: `src/main/java/com/example/route/RouteRegistry.java`, `src/test/java/com/example/route/FakeRoutesDb.java`
- Test: `RouteRegistryTableTest`

**Interfaces:**
- Consumes: `TableRecord.getColumnNames()` from Task 3.
- Produces: `private String readFingerprint(Database db)`: reads every `routes` row ordered by `id`, joins all column values, returns an MD5 hex string computed in Java. Any change to any column changes it.
- Produces: `loadFromTable` reads rows where `active = true`, ordered by `sort_order` then `id`. `toRoute` takes a `TableRecord`. Numbers and booleans parse from `getValue` text.

- [ ] **Step 1: Update `FakeRoutesDb`** to answer the plain `select` on `routes` for both reads. Drop its `md5` fingerprint branch.
- [ ] **Step 2: Run** `mvn test -Dtest='RouteRegistryTableTest,RouteRegistryTest,RouteDispatcherTest'`. Expected: FAIL before the change.
- [ ] **Step 3: Implement** the two methods above. Delete `FINGERPRINT_SQL` and the jOOQ imports.
- [ ] **Step 4: Run** the same tests. Expected: PASS, including the "changing a handler changes the fingerprint" case.
- [ ] **Step 5: Commit** `refactor: RouteRegistry reads through TableRecord`.

### Task 9: Rules, docs and full build

**Files:**
- Modify: `.claude/rules/data-access.md`, `docs/scripting/table-records-and-rules.md`, `docs/architecture/configuration.md`, `CLAUDE.md`, the design spec

- [ ] **Step 1: `.claude/rules/data-access.md`**: replace the Allow-list section with "unset means all tables; set `RECORD_ALLOWED_TABLES` to restrict". Add `DatabaseAttachmentStorage` (binary chunks in one transaction) and `TableApi` (generic browser over `information_schema`) to the exempt list, each with its reason. Add one line: ACL checks will live in a `TableRecordSecure` wrapper, not in `TableRecord`.
- [ ] **Step 2: `table-records-and-rules.md`**: document `getColumnNames`, the read-only behaviour for tables with no single-column key, and the setting.
- [ ] **Step 3: `configuration.md` and `CLAUDE.md`**: `application.yml` holds db credentials and `record.allowedTables`. Fix the "db credentials only" lines.
- [ ] **Step 4: Spec**: change decision 2 to "a table without a single-column primary key is read-only: `query`, `next`, `count` work; every write throws." Reason: bulk writes target rows by key.
- [ ] **Step 5: Run** `mvn spotless:apply && mvn clean verify`. Expected: BUILD SUCCESS apart from the three known `RouteDispatcher` errors in CLAUDE.md. Report any other failure.
- [ ] **Step 6: Commit** `docs: TableRecord on all tables`.
