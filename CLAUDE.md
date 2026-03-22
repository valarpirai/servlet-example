# CLAUDE.md

**Last updated**: 2026-03-22

Quick guidance for Claude Code. **All details in topic-specific docs below.**

## 🚀 Where to Look

**Task** | **Doc** | **Key Files**
---|---|---
Routing | docs/ROUTE-REGISTRY.md | routes.yml, route/
File storage | docs/MEMORY-GUARANTEE.md | storage/
JS security | docs/SCRIPT-SECURITY.md | ScriptProcessor.java:39-147
Databases | docs/data-browser.md | datasource/
Logging | docs/STRUCTURED-LOGGING.md | util/StructuredLogger.java
Code structure | docs/CODEBASE-MAP.md | All packages
Issues/fixes | docs/TROUBLESHOOTING.md | -
Dev setup | docs/DEVELOPMENT.md | pom.xml

## ⚠️ CRITICAL

**1. NEVER `new Gson()`** → Always `JsonUtil.toJson()` / `fromJson()`
- Why: Custom `InstantTypeAdapter` for `java.time.Instant` fields
- File: `storage/LocalFileSystemStorage.java:saveMetadata()`, `loadMetadata()`

**2. NEVER load entire files** → Always stream with `InputStream`/`OutputStream`
- Why: 500MB file = 1MB heap (not 500MB heap)
- Doc: docs/MEMORY-GUARANTEE.md

**3. NEVER mix writers** → Downloads use `getOutputStream()`, JSON uses `getWriter()`

---

## Commands

```bash
mvn -PappRun                         # Dev server
mvn clean package                    # Build JAR
mvn test                             # 72 tests (~15s)
mvn spotless:apply                   # Format code

SERVER_PORT=9090 mvn -PappRun        # Custom port
curl localhost:8080/health           # Health check
```

---

## Config

`src/main/resources/application.yml` - server.port (8080), storage.chunkSize (1MB), script.timeout (5s)
`src/main/resources/routes.yml` - All 21 HTTP routes

**Env vars override YAML**: `SERVER_PORT=9090`

---

## Architecture

**Routing**: 21 routes in YAML → RouteRegistry → RouteDispatcher → Handler/Processor
**Storage**: 1MB chunks → ChunkedOutputStream/InputStream → memory-safe
**JS**: Rhino sandbox → ClassShutter (whitelist/blacklist) → 26 security tests
**DB**: Strategy pattern → PostgreSQL/MySQL/Snowflake → on-demand drivers

**Structure**: route/, processor/, storage/, handler/, datasource/, util/

**See docs/CODEBASE-MAP.md for full details**

---

## Before Modifying

**Routes**: Check routes.yml → Register in RouteDispatcher → `mvn test -Dtest=Route*Test`
**Storage**: Use JsonUtil → Stream files → Update cache → `mvn test -Dtest=*Storage*Test`
**Security**: Check ScriptProcessor.java:39-147 → `mvn test -Dtest=ScriptProcessorSecurityTest`

**Full checklists in docs**

---

## Stuck?

1. **docs/TROUBLESHOOTING.md** - 20+ common issues with solutions
2. **docs/CODEBASE-MAP.md** - Navigate packages, find files
3. **Run relevant tests** - Understand expected behavior
4. **Enable DEBUG logging** - Edit application.yml
