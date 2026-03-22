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

---

## 🔀 Quick Decision Tree

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
- Looking for file? → Use File Patterns below
- Understanding flow? → Check Component Dependencies in docs/CODEBASE-MAP.md

## ⚠️ CRITICAL

**1. NEVER `new Gson()`** → Always `JsonUtil.toJson()` / `fromJson()`
- Why: Custom `InstantTypeAdapter` for `java.time.Instant` fields
- File: `storage/LocalFileSystemStorage.java:saveMetadata()`, `loadMetadata()`

**2. NEVER load entire files** → Always stream with `InputStream`/`OutputStream`
- Why: 500MB file = 1MB heap (not 500MB heap)
- Doc: docs/MEMORY-GUARANTEE.md

**3. NEVER mix writers** → Downloads use `getOutputStream()`, JSON uses `getWriter()`

---

## ⚠️ KNOWN ISSUES

| Component | Status | Issue | Workaround |
|-----------|--------|-------|------------|
| API Scripting | ⚠️ Partial | 11/17 tests passing - request object conversion issues | Use core ScriptProcessor instead. See docs/API-SCRIPTING.md:214 |
| ScriptProcessor | ⚠️ Minor | 2/26 tests fail (ArrayList/HashMap conversion) | Known pre-existing issue, doesn't affect production usage |

**Before using API Scripting feature**: Read docs/API-SCRIPTING.md section "Known Issues" for details on request object limitations.

---

## 🔍 File Patterns (Quick Locate)

**Configuration:**
- Main config: `src/main/resources/application.yml`
- Routes: `src/main/resources/routes.yml`

**Source code (by package):**
- All processors: `src/main/java/**/processor/*Processor.java`
- All handlers: `src/main/java/**/handler/*Handler.java`
- All strategies: `src/main/java/**/datasource/*Strategy.java`
- Storage layer: `src/main/java/**/storage/*.java`
- Utilities: `src/main/java/**/util/*.java`
- Route system: `src/main/java/**/route/*.java`

**Tests:**
- All tests: `src/test/java/**/*Test.java`
- Tests for X: `src/test/java/**/*{X}*Test.java`
- Route tests: `src/test/java/**/route/*Test.java`
- Storage tests: `src/test/java/**/storage/*Test.java`
- Security tests: `src/test/java/**/processor/ScriptProcessorSecurityTest.java`

**Documentation:**
- All docs: `docs/*.md`
- Main guide: `CLAUDE.md`

**JavaScript (if using API Scripting):**
- API scripts: `scripts/api/*.js`
- Shared libs: `scripts/lib/*.js`

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

## 📝 Common Recipes

### Recipe: Add New REST Endpoint
```bash
# 1. Read and understand route format
Read src/main/resources/routes.yml

# 2. Add route entry (specific routes before wildcards)
Edit routes.yml → Add new route definition

# 3. Register processor/handler
Read src/main/java/.../RouteDispatcher.java
Edit RouteDispatcher.java → Add case in getProcessorInstance() or getHandlerInstance()

# 4. Create implementation
Write src/main/java/.../MyProcessor.java → Implement IRequestProcessor

# 5. Write test
Write src/test/java/.../MyProcessorTest.java

# 6. Validate
mvn test -Dtest=MyProcessorTest
mvn test -Dtest=Route*Test

# 7. Manual test
mvn -PappRun
curl localhost:8080/api/your-endpoint

# 8. Format
mvn spotless:apply
```

**Expected result**: `curl localhost:8080/api/your-endpoint` returns 200 OK

### Recipe: Fix JSON Serialization Error
```bash
# 1. Identify the error
grep "JsonIOException" logs/application.log

# 2. Find problematic code
Glob **/*Storage*.java
Read suspected files

# 3. Replace new Gson() with JsonUtil
Edit file → Replace "new Gson()" with "JsonUtil"

# 4. Test
mvn test -Dtest=*Storage*Test
mvn test -Dtest=JsonUtilTest

# 5. Verify
mvn clean package
```

**Critical**: Always use `JsonUtil.toJson()` / `fromJson()` for `java.time.Instant` fields

### Recipe: Add New Database Support
```bash
# 1. Create strategy
Write src/main/java/.../datasource/MyDbStrategy.java → Implement DataSourceStrategy

# 2. Register strategy
Edit src/main/java/.../datasource/DataSourceRegistry.java → Add to constructor

# 3. Update UI (if needed)
Edit src/main/resources/static/data-browser.html → Add to DB_FIELDS

# 4. Write tests
Write src/test/java/.../MyDbStrategyTest.java

# 5. Validate
mvn test -Dtest=DataSource*Test
```

**No other changes needed**: ExtLibManager and DataBrowserHandler work automatically

### Recipe: Debug Route 404
```bash
# 1. Check route exists
Read src/main/resources/routes.yml → Find your route

# 2. Check route order (specific before wildcards)
Verify order in routes.yml

# 3. Check registration
Read src/main/java/.../RouteDispatcher.java
Verify processor/handler is registered

# 4. Test route matching
mvn test -Dtest=RouteRegistryTest

# 5. Enable debug logging
Edit src/main/resources/application.yml → Set com.example: DEBUG

# 6. Check logs
mvn -PappRun
curl your-endpoint
tail -f logs/application.log
```

### Recipe: Fix Memory Issue
```bash
# 1. Check for non-streaming code
Grep "Files.readAllBytes" or "byte\[\]" in src/main/java

# 2. Replace with streaming
Edit file → Use InputStream/OutputStream instead

# 3. Verify chunking used
Read storage/LocalFileSystemStorage.java → Confirm ChunkedOutputStream usage

# 4. Test memory
mvn test -Dtest=*Storage*Test
curl localhost:8080/metrics | jq '.metrics.memory'

# 5. Load test (if needed)
# Upload large file, check memory stays < 100MB
```

**See docs/MEMORY-GUARANTEE.md for streaming patterns**

---

## Before Modifying

**Routes**: Check routes.yml → Register in RouteDispatcher → `mvn test -Dtest=Route*Test`
**Storage**: Use JsonUtil → Stream files → Update cache → `mvn test -Dtest=*Storage*Test`
**Security**: Check ScriptProcessor.java:39-147 → `mvn test -Dtest=ScriptProcessorSecurityTest`

**Full checklists in docs**

---

## 📚 Reading Priority (Context Optimization)

**Always read first:**
1. `CLAUDE.md` (this file) - Critical warnings and quick reference
2. `docs/TROUBLESHOOTING.md` - If fixing a bug or issue

**Read based on task type:**
- Adding route? → `docs/ROUTE-REGISTRY.md` **only**
- Storage issue? → `docs/MEMORY-GUARANTEE.md` + `storage/LocalFileSystemStorage.java`
- Security concern? → `docs/SCRIPT-SECURITY.md:1-150` (summary section)
- Database work? → `docs/data-browser.md` + relevant `*Strategy.java`
- Logging work? → `docs/STRUCTURED-LOGGING.md`
- General exploration? → `docs/CODEBASE-MAP.md`
- Development setup? → `docs/DEVELOPMENT.md`

**Don't read unless needed:**
- `docs/DOCUMENTATION-IMPROVEMENTS.md` - Meta-documentation
- `docs/REFACTORING-SUMMARY.md` - Historical context
- `docs/API-SCRIPTING.md` - Only if using scripted APIs (has known issues)

**Token optimization tip**: Use TL;DR sections at top of docs, skip detailed sections if not needed.

---

## Stuck?

1. **docs/TROUBLESHOOTING.md** - 20+ common issues with solutions
2. **docs/CODEBASE-MAP.md** - Navigate packages, find files
3. **Run relevant tests** - Understand expected behavior
4. **Enable DEBUG logging** - Edit application.yml
