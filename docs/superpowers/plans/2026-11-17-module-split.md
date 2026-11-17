# Module Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox syntax.

**Goal:** Split the single app into `common`, `core`, `scheduler` and `web` Maven modules, each with its own tests.

**Architecture:** An aggregator root POM lists four modules. Dependencies flow `common ← core ← scheduler ← web`. Classes move with `git mv`. Package names stay `com.example.*`.

**Tech Stack:** Maven multi-module, `shared-build-parent` as module parent, JUnit 5, Mockito, shade plugin in `web`.

**Spec:** [2026-11-17-module-split-design.md](../specs/2026-11-17-module-split-design.md)

## Global Constraints

- No version in any module POM. Versions come from `shared-build/catalog/`.
- Package names do not change. Reflection strings in `RouteRegistry` and `RouteDispatcher` stay valid.
- `core` and `scheduler` never import `jakarta.servlet`.
- All migrations stay in `core` (`db/V*.sql`).
- Baseline: 458 tests, 3 known `RouteDispatcherTest` errors. The total must stay 458 with the same 3 errors.

## Review Focus

- Shaded JAR still merges `META-INF/services` (web) — check `java -jar` boots.
- `JobHandlerScanner` finds `@JobName` classes in `web` classpath — `scantest` runs in `scheduler`.
- `StorageTestHelper` wipes `data/attachments` — storage tests stay in `web` only.
- Each module has at least one test class that runs in its own `mvn -pl`.
- `mvn spotless:check` passes in every module.

## Deviation from the spec

`script/ScriptScopeBuilder` imports `HttpServletRequest`. Only `ScriptProcessor` uses it.
It moves to `web`, package `com.example.processor`. Core stays servlet-free.

## Task 1: Aggregator and web shell

**Files:** Create `pom.xml` (aggregator), `web/pom.xml`. Move `src/` to `web/src/`.

- [ ] Root `pom.xml`: parent `shared-build-parent`, `packaging` pom, modules `common`, `core`, `scheduler`, `web`. Artifact `servlet-example-parent`.
- [ ] `git mv src web/src`; `web/pom.xml` carries the old dependencies, shade, exec, `app.mainClass`.
- [ ] Run `mvn -o test`. Expect 458 tests, same 3 errors.
- [ ] Commit.

## Task 2: common

**Interfaces — Produces:** `exception/*`, `model/*`, `util.JsonUtil`, `util.JsonConverter`.

- [ ] Create `common/pom.xml`: gson, lombok (provided), slf4j-api, junit, mockito.
- [ ] `git mv` those packages and `JsonUtilTest` to `common/src/{main,test}/java/com/example/...`.
- [ ] `web/pom.xml` and later modules depend on `common`.
- [ ] Run `mvn -o -pl common test`, then `mvn -o test`. Totals match baseline.

## Task 3: core

**Interfaces — Produces:** `config/*`, `repository.PropertyRepository`, `record/*`, `script/*` (not `ScriptScopeBuilder`), `module/*`, `security/*`, `processor.ApiScriptProcessor`, `util.{StructuredLogger,LoggingConfig,PropertiesUtil,RuntimeMetrics}`, resources `db/`, `application.yml`, `logback.xml`.

- [ ] Create `core/pom.xml`: common, gson, snakeyaml, rhino, slf4j, logback, janino, lombok, postgresql, HikariCP, jOOQ, flyway, test deps.
- [ ] Move main classes, resources and matching tests (`config`, `module`, `record`, `script`, `PropertyRepositoryTest`, `ApiScriptProcessorTest`, `FakeScriptDb`, `PropertiesUtilTest`, `RuntimeMetricsTest`).
- [ ] Move `ScriptScopeBuilder` to `web/.../processor/`; change its package; fix `ScriptProcessor` import.
- [ ] Run `mvn -o -pl core test`, then `mvn -o test`.

## Task 4: scheduler

**Interfaces — Produces:** `scheduler/*`, `repository.{JobRepository,SqlJobRepository}`.

- [ ] Create `scheduler/pom.xml`: core, classgraph, test deps.
- [ ] Move classes; move tests `scheduler/*`, `scantest/*`, `FakeJobRepository`.
- [ ] Run `mvn -o -pl scheduler test`, then `mvn -o test`.

## Task 5: web cleanup and verify

- [ ] `web/pom.xml` depends on `scheduler`, `core`, `common` and tomcat.
- [ ] Run `mvn spotless:apply`, then `mvn -o clean verify`. Totals match baseline.
- [ ] Run `java -jar web/target/servlet-example.jar` if PostgreSQL is up; hit `/health`.

## Task 6: Docs and commit

- [ ] Update `package-map.md`, `build-system.md`, `workflows.md`, `CLAUDE.md` (paths and commands).
- [ ] Update the spec for the `ScriptScopeBuilder` move.
- [ ] Commit by explicit path, never `git add -A`.
