# CLAUDE.md

**Last updated**: 2026-04-21

Quick guidance for Claude Code. **All details in topic-specific docs below.**

## Where to Look

**Task** | **Doc** | **Key Files**
---|---|---
Routing | docs/ROUTE-REGISTRY.md | routes.yml, route/
File storage | docs/MEMORY-GUARANTEE.md | storage/
JS security | docs/SCRIPT-SECURITY.md | ScriptProcessor.java:39-147
Databases | docs/data-browser.md | datasource/
Logging | docs/STRUCTURED-LOGGING.md | util/StructuredLogger.java
Code structure | docs/CODEBASE-MAP.md | All packages
App flow diagrams | docs/APP-FLOW.md | config/, servlet/
Issues/fixes | docs/TROUBLESHOOTING.md | -
Dev setup | docs/DEVELOPMENT.md | pom.xml

---

## Quick Decision Tree

**Adding functionality?**
- New HTTP endpoint? → Add to `routes.yml` → Register in `RouteDispatcher` → See docs/ROUTE-REGISTRY.md
- JavaScript API? → Create `scripts/api/{name}.js` → See docs/API-SCRIPTING.md
- Database type? → Create `{DB}Strategy.java` → Register in `DataSourceRegistry` → See docs/data-browser.md
- File storage feature? → Modify `storage/` → Use streaming APIs → See docs/MEMORY-GUARANTEE.md
- Logging feature? → Use `StructuredLogger` → See docs/STRUCTURED-LOGGING.md

**Fixing a bug?**
- Test failing? → **Read docs/TROUBLESHOOTING.md first**
- JSON error? → **Use `JsonUtil` NOT `new Gson()`** → See `storage/LocalFileSystemStorage.java`
- Memory issue? → Check streaming usage → See docs/MEMORY-GUARANTEE.md
- Route 404? → Check `routes.yml` order (specific before wildcards) → See docs/ROUTE-REGISTRY.md
- Script timeout? → Check `application.yml` limits → See docs/SCRIPT-SECURITY.md
- Build error? → Run `mvn spotless:apply` → See docs/DEVELOPMENT.md

**Exploring codebase?**
- First time? → Read docs/CODEBASE-MAP.md
- App startup / request flow? → Read docs/APP-FLOW.md
- Looking for file? → Use File Patterns below

## CRITICAL

**1. NEVER `new Gson()`** → Always `JsonUtil.toJson()` / `fromJson()`
- Why: Custom `InstantTypeAdapter` for `java.time.Instant` fields

**2. NEVER load entire files** → Always stream with `InputStream`/`OutputStream`
- Why: 500MB file = 1MB heap — see docs/MEMORY-GUARANTEE.md

**3. NEVER mix writers** → Downloads use `getOutputStream()`, JSON uses `getWriter()`

**4. DB access goes through `Database.query()` / `transact()`** — never raw JDBC except `openConnection()` for cursors

---

## Known Issues

| Component | Status | Issue | Workaround |
|-----------|--------|-------|------------|
| API Scripting | Partial | 11/17 tests passing - request object conversion issues | Use core ScriptProcessor. See docs/API-SCRIPTING.md:214 |
| ScriptProcessor | Minor | 2/26 tests fail (ArrayList/HashMap conversion) | Pre-existing, doesn't affect production |

---

## File Patterns

**Configuration:**
- Main config: `src/main/resources/application.yml`
- Routes: `src/main/resources/routes.yml`
- DB migrations: `src/main/resources/db/V*.sql`

**Source code (by package):**
- DB / startup: `src/main/java/**/config/*.java`
- All processors: `src/main/java/**/processor/*Processor.java`
- All handlers: `src/main/java/**/handler/*Handler.java`
- All strategies: `src/main/java/**/datasource/*Strategy.java`
- Storage layer: `src/main/java/**/storage/*.java`
- Utilities: `src/main/java/**/util/*.java`
- Route system: `src/main/java/**/route/*.java`

**Tests:**
- All tests: `src/test/java/**/*Test.java`
- Config tests: `src/test/java/**/config/*Test.java`
- Route tests: `src/test/java/**/route/*Test.java`
- Storage tests: `src/test/java/**/storage/*Test.java`

---

## Commands

```bash
mvn -PappRun                         # Dev server
mvn clean package                    # Build JAR
mvn test                             # All tests
mvn spotless:apply                   # Format code

SERVER_PORT=9090 mvn -PappRun        # Custom port
curl localhost:8080/health           # Health check
```

---

## Config

`src/main/resources/application.yml` — server.port (8080), storage.chunkSize (1MB), script.timeout (5s)
`src/main/resources/routes.yml` — 27 HTTP routes

Optional DB-backed properties (all fall back to YAML if absent):
```yaml
db:
  url: jdbc:postgresql://localhost:5432/mydb
  username: user
  password: secret
storage:
  type: database   # or: local (default)
```

**Env vars override YAML**: `SERVER_PORT=9090`

---

## Architecture

**Routing**: 27 routes in YAML → RouteRegistry → RouteDispatcher → Handler/Processor
**Properties**: LRU cache → DB (`app_properties`) → YAML (three-level fallback)
**Storage**: 1MB chunks → ChunkedOutputStream/InputStream → memory-safe; DB or local backend
**DB**: `Database` wrapper (jOOQ DSL) + Flyway auto-migrations on startup
**JS**: Rhino sandbox → ClassShutter (whitelist/blacklist)

**Packages**: config/, route/, processor/, storage/, handler/, datasource/, util/

**See docs/CODEBASE-MAP.md and docs/APP-FLOW.md for full details**

---

## Stuck?

1. **docs/TROUBLESHOOTING.md** — 20+ common issues with solutions
2. **docs/CODEBASE-MAP.md** — navigate packages, find files
3. **docs/APP-FLOW.md** — startup and request flow diagrams
4. Run relevant tests to understand expected behavior
5. Enable DEBUG logging in `application.yml`
