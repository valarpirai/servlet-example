# CLAUDE.md

Quick guidance for Claude Code. **All detail lives in docs/ — start at
[docs/readme.md](docs/readme.md).**

## Where to Look

**Task** | **Doc** | **Key Files**
---|---|---
Routing | docs/routing/table-routes.md | `routes` table, route/
File storage | docs/storage/memory-guarantee.md | storage/
Table browser, editing | docs/data-browser/table-browser.md, table-editing.md | tableapi/, handler/TableApiHandler.java, static/table-browser.html
JS security | docs/scripting/sandbox-policy.md | security/ScriptSecurityManager.java
Databases | docs/data-browser/datasource-strategies.md | datasource/
Logging | docs/observability/structured-logging.md | util/StructuredLogger.java
Code structure | docs/architecture/package-map.md | All packages
App flow diagrams | docs/architecture/startup-flow.md | config/, servlet/
Configuration | docs/architecture/configuration.md | application.yml, app_properties
Issues/fixes | docs/troubleshooting/ | -
Dev setup | docs/development/workflows.md | pom.xml
Build system / versions | docs/build/build-system.md | shared-build/
Writing docs | .claude/rules/documentation.md | docs/

---

## Quick Decision Tree

**Adding functionality?**
- New HTTP endpoint? → Java handler: row in `routes` (migration) → register in **both** `RouteRegistry` and `RouteDispatcher`. JavaScript handler: `script` route, no Java → See docs/routing/adding-a-route.md
- JavaScript API? → Insert a row into `api_scripts` (name = endpoint) → See docs/scripting/api-scripting.md
- Database type? → Create `{DB}Strategy.java` → Add to `META-INF/services/com.example.datasource.DataSourceStrategy` → See docs/data-browser/datasource-strategies.md
- File storage feature? → Modify `storage/` → Use streaming APIs → See docs/storage/memory-guarantee.md
- Logging feature? → Use `StructuredLogger` → See docs/observability/structured-logging.md
- New dependency? → Add to `shared-build/catalog/thirdparty/pom.xml` → declare **without version** in `pom.xml` → See docs/build/adding-a-dependency.md
- Writing or editing docs? → **Read .claude/rules/documentation.md first**

**Fixing a bug?**
- Test failing? → **Read docs/troubleshooting/ first**
- JSON error? → **Use `JsonUtil` NOT `new Gson()`** → See docs/troubleshooting/json-and-serialization.md
- Memory issue? → Check streaming usage → See docs/storage/memory-guarantee.md
- Route 404? → Check the `routes` row, its `sort_order` and **both** registration points → See docs/troubleshooting/routing-issues.md
- Script timeout? → `script.timeout` in `app_properties` → See docs/troubleshooting/script-issues.md
- Build error? → Run `mvn spotless:apply` → See docs/troubleshooting/build-issues.md
- Enforcer failure? → A transitive dep wants a newer version → pin it in `shared-build/catalog/thirdparty/pom.xml`

**Exploring codebase?**
- First time? → Read docs/readme.md then docs/architecture/package-map.md
- App startup / request flow? → docs/architecture/startup-flow.md, docs/architecture/request-flow.md

## CRITICAL

**1. NEVER `new Gson()`** → Always `JsonUtil.toJson()` / `fromJson()`
- Why: Custom `InstantTypeAdapter` for `java.time.Instant` fields

**2. NEVER load entire files** → Always stream with `InputStream`/`OutputStream`
- Why: heap must scale with concurrency, not file size — see docs/storage/memory-guarantee.md

**3. NEVER mix writers** → Downloads use `getOutputStream()`, JSON uses `getWriter()`

**4. DB access goes through `Database.query()` / `transact()`** — never raw JDBC except `openConnection()` for cursors

**5. NEVER hardcode a dependency version in `pom.xml`** → Pin it in `shared-build/catalog/thirdparty/pom.xml`, then declare it version-free
- Why: one version of each library, changed in one reviewed place — see docs/build/build-system.md

**6. NEVER edit an applied Flyway migration** → Add a new `V{n}__` file
- Why: Flyway validates checksums of applied migrations; editing one breaks startup on every existing database

**7. Handler and processor names are registered TWICE** → `RouteRegistry` for validation, `RouteDispatcher` for dispatch
- Why: they are string literals, so a rename compiles cleanly and fails at runtime

---

## Known Issues

| Component | Status | Issue | Workaround |
|-----------|--------|-------|------------|
| RouteDispatcher | Minor | 3/371 tests error: `testDeleteHandlerRoute`, `testHandlerRouteDownload`, `testHandlerRouteWithPathParams` ("Handler invocation failed") | Pre-existing. Routes work at runtime — verified via `/api/properties`, `/health`, `/metrics` |
| Storage tests | Minor | `StorageTestHelper` deletes `data/attachments` recursively, so running the suite wipes locally stored attachments | By design, but it will surprise you once |

---

## File Patterns

**Build:**
- Conventions (plugins, compiler, Java version): `shared-build/pom.xml`
- Third-party versions: `shared-build/catalog/thirdparty/pom.xml`
- Internal/org versions: `shared-build/catalog/internal/pom.xml`
- App dependencies (no versions): `pom.xml`

**Configuration:**
- DB credentials only: `src/main/resources/application.yml`
- Everything else: `app_properties` table, seeded by `db/V2__seed_app_properties.sql`
- Routes: the `routes` table
- DB migrations: `src/main/resources/db/V*.sql`

**Source code (by package):**
- DB / startup: `src/main/java/**/config/*.java`
- All processors: `src/main/java/**/processor/*Processor.java`
- All handlers: `src/main/java/**/handler/*Handler.java`
- All strategies: `src/main/java/**/datasource/*Strategy.java`
- Storage layer: `src/main/java/**/storage/*.java`
- Utilities: `src/main/java/**/util/*.java`
- Route system: `src/main/java/**/route/*.java`

**Runtime state (all gitignored, all under `data/`):**
- Attachments: `data/attachments/`
- Downloaded JDBC driver jars: `data/extlib/`
- Logs: `logs/`

**Tests:**
- All tests: `src/test/java/**/*Test.java`
- Tests mirror source packages exactly

---

## Commands

```bash
mvn clean verify                     # Build, test, format check, enforcer
mvn -PappRun                         # Dev server
mvn clean package                    # Build JAR
java -jar target/servlet-example.jar # Run the packaged JAR
mvn test                             # All tests
mvn spotless:apply                   # Format code

SERVER_PORT=9090 mvn -PappRun        # Custom port
curl localhost:8080/health           # Health check
```

PostgreSQL is required; the app exits at startup without it.
See docs/architecture/configuration.md.

---

## Config

`src/main/resources/application.yml` — **db credentials only**
The `routes` table — 38 default routes, seeded by `db/V8__create_routes.sql` and changed by later migrations.

Every other property lives in `app_properties`, **not** `application.yml`.
Lookup order is LRU cache → DB → YAML.

Env vars are **not** a general override — only `${VAR}` placeholders in
`application.yml` plus `SERVER_PORT`.

Defaults, env var rules, and the connection pool: docs/architecture/configuration.md.

---

## Architecture

**Routing**: `routes` table → RouteRegistry → RouteDispatcher → Handler/Processor
**Properties**: LRU cache → DB (`app_properties`) → YAML (three-level fallback)
**Storage**: 1MB chunks → ChunkedOutputStream/InputStream → memory-safe; DB or filesystem backend
**DB**: `Database` wrapper (jOOQ DSL) + Flyway auto-migrations on startup
**JS**: Rhino sandbox → `ScriptSecurityManager` ClassShutter (whitelist/blacklist)
**Build**: catalog-thirdparty → catalog-internal → shared-build-parent → app (Maven parent chain)

**Packages**: `com.example.*` — servlet/, route/, processor/, handler/, storage/, datasource/, config/,
script/, security/, module/, extlib/, mongo/, model/, exception/, util/

**See docs/architecture/ for full details**

---

## Stuck?

1. **docs/troubleshooting/** — symptom-first fixes by area
2. **docs/architecture/package-map.md** — navigate packages, find files
3. **docs/architecture/startup-flow.md** and **request-flow.md** — flow diagrams
4. Run relevant tests to understand expected behavior
5. Raise `logging.level` in `app_properties`
