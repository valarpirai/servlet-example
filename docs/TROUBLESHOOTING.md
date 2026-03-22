# Troubleshooting Guide

**Last updated**: 2026-03-22

Quick solutions to common issues encountered when working with this codebase.

## JSON Serialization Errors

### "JsonIOException: Failed writing Instant"

**Symptoms**: Exception when serializing objects with `java.time.Instant` fields.

**Cause**: Using `new Gson()` instead of `JsonUtil`.

**Fix**:
```java
// ❌ WRONG
Gson gson = new Gson();
String json = gson.toJson(attachment);

// ✅ CORRECT
String json = JsonUtil.toJson(attachment);
```

**Why**: `JsonUtil` has a custom `InstantTypeAdapter` registered for `java.time.Instant` fields.

**Affected files**:
- `storage/LocalFileSystemStorage.java:saveMetadata()` (fixed in commit 5e6eabd)
- `storage/LocalFileSystemStorage.java:loadMetadata()` (fixed in commit 5e6eabd)

**Reference**: CLAUDE.md "Common Pitfalls" section

---

## Memory Issues

### OutOfMemoryError

**Symptoms**: `java.lang.OutOfMemoryError: Java heap space`

**Diagnosis**: This should be extremely rare due to chunked storage.

**Check**:
1. Are you bypassing `ChunkedOutputStream`/`ChunkedInputStream`?
2. Are you loading entire files with `Files.readAllBytes()`?
3. Check concurrent requests: `curl http://localhost:8080/metrics | jq '.metrics.concurrentRequests'`

**Fix**:
- Use streaming APIs: `InputStream`/`OutputStream`, not byte arrays
- Verify chunk size: Check `application.yml` → `storage.chunkSize`
- Monitor: `curl http://localhost:8080/metrics | jq '.metrics.memory'`

**Reference**: docs/MEMORY-GUARANTEE.md

---

## Routing Issues

### 404 Not Found (Route Not Working)

**Symptoms**: New route returns 404 JSON response.

**Checklist**:
1. ✅ Route defined in `src/main/resources/routes.yml`?
2. ✅ HTTP method matches (GET, POST, PUT, DELETE)?
3. ✅ Path pattern correct (check wildcards and `{params}`)?
4. ✅ Route order (specific routes before wildcards)?
5. ✅ Processor registered in `RouteDispatcher.getProcessorInstance()`?
6. ✅ Handler registered in `RouteDispatcher.getHandlerInstance()`?
7. ✅ Server restarted after routes.yml change?

**Debug**:
```java
// Add breakpoint at:
RouteRegistry.java:findRoute() // Check if route is loaded
RouteDispatcher.java:dispatch() // Check if dispatch succeeds
```

**Test**:
```bash
mvn test -Dtest=RouteRegistryTest
mvn test -Dtest=RouteDispatcherTest
```

**Reference**: docs/ROUTE-REGISTRY.md

---

## Script Execution Errors

### Script Timeout

**Symptoms**: `{"status":"error","message":"Script execution timeout"}`

**Cause**: Script runs longer than configured timeout (default: 5000ms).

**Fix**:
```yaml
# src/main/resources/application.yml
script:
  timeout: 10000  # Increase to 10 seconds
```

**Or optimize script**: Check for infinite loops or expensive operations.

**Test**:
```bash
# Should timeout (default 5s)
curl -X POST http://localhost:8080/api/script \
  -H "Content-Type: application/javascript" \
  -d '{"script":"while(true){}"}'
```

**Reference**: docs/SCRIPT-SECURITY.md

### ClassShutter Block

**Symptoms**: `{"status":"error","message":"Access to class X is not allowed"}`

**Cause**: Script trying to access blocked Java classes.

**Allowed classes**: Collections, primitives, Date/Time, Math
**Blocked classes**: System, Runtime, File, Network, Reflection, Database

**Reference**:
- `ScriptProcessor.java:39-147` (ClassShutter implementation)
- docs/SCRIPT-SECURITY.md (full whitelist/blacklist)

---

## Database Browser Issues

### Driver Not Downloaded

**Symptoms**: "JDBC driver not found" error when connecting.

**Fix**:
1. Click "↓ Download Driver" button in UI
2. Or manually: `curl -X POST http://localhost:8080/api/data-browser/download -H "Content-Type: application/json" -d '{"dbType":"postgresql"}'`
3. Verify: Check `extlib/` directory for JAR files

**Driver paths** (Maven Central):
- PostgreSQL: `org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar`
- MySQL: `com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar`
- Snowflake: `net/snowflake/snowflake-jdbc/3.15.0/snowflake-jdbc-3.15.0.jar`

### Connection Timeout

**Symptoms**: "Connection refused" or timeout errors.

**Check**:
1. Database server running?
2. Firewall/network access?
3. Correct hostname/port in connection string?
4. Credentials valid?

**Test connection**:
```bash
# PostgreSQL
psql -h localhost -p 5432 -U username -d database

# MySQL
mysql -h localhost -P 3306 -u username -p database
```

**Reference**: docs/data-browser.md

---

## Build Issues

### "Spotless check failed"

**Symptoms**: Pre-commit hook rejects commit due to formatting.

**Fix**:
```bash
mvn spotless:apply
git add .
git commit -m "your message"
```

**Auto-format on save**: Configure your IDE (IntelliJ/VSCode/Eclipse).

**Reference**: docs/DEVELOPMENT.md

### Tests Failing

**Symptoms**: `mvn test` fails.

**Diagnose**:
```bash
# Run specific test class
mvn test -Dtest=FailingTestClass

# Run with stack traces
mvn test -Dtest=FailingTestClass -X

# Skip tests temporarily (not recommended)
mvn package -DskipTests
```

**Common causes**:
1. Port 8080 already in use (tests start embedded server)
2. Files from previous test runs not cleaned up
3. Outdated dependencies

**Fix**:
```bash
# Clean build
mvn clean test

# Kill processes on port 8080
lsof -ti:8080 | xargs kill -9
```

---

## Runtime Issues

### Port Already in Use

**Symptoms**: `java.net.BindException: Address already in use`

**Fix**:
```bash
# Find process using port 8080
lsof -ti:8080

# Kill process
lsof -ti:8080 | xargs kill -9

# Or use different port
SERVER_PORT=9090 mvn -PappRun
```

### Metadata Cache Out of Sync

**Symptoms**: Attachment exists on disk but not in `/api/attachments` list.

**Cause**: Server crashed before metadata cache persisted, or manual file changes.

**Fix**: Restart server (cache rebuilds from `metadata.json` files on startup).

**Verify**:
```bash
# Check metadata files exist
find attachments -name "metadata.json" | wc -l

# Should match attachment count from API
curl http://localhost:8080/api/attachments | jq '.data | length'
```

---

## Performance Issues

### Slow Request Processing

**Check metrics**:
```bash
curl http://localhost:8080/metrics | jq
```

**Look for**:
- High memory usage (> 80% of max heap)
- High concurrent request count
- Long average response times

**Solutions**:
1. Increase heap: `java -Xmx2048m -jar servlet-example.jar`
2. Check slow queries in logs (grep for high `responseTimeMs`)
3. Enable structured logging to identify bottlenecks

**Reference**: docs/STRUCTURED-LOGGING.md

---

## Development Environment

### Lombok Not Working

**Symptoms**: IDE shows errors on `@Getter`, `@Setter`, `@Builder`.

**Fix**:

**IntelliJ IDEA**:
1. Install Lombok plugin: Preferences → Plugins → Lombok
2. Enable annotation processing: Preferences → Build → Compiler → Annotation Processors

**VS Code**:
1. Install "Language Support for Java" extension
2. Restart VS Code

**Eclipse**:
1. Download lombok.jar: https://projectlombok.org/download
2. Run: `java -jar lombok.jar`
3. Select Eclipse installation directory

**Reference**: docs/DEVELOPMENT.md

---

## Getting Help

### Check Logs

**Default location**: `logs/` directory (if configured)

**View recent logs**:
```bash
tail -f logs/application.log
```

**Search logs by correlation ID**:
```bash
grep "correlationId=abc-123" logs/application.log
```

**Reference**: docs/STRUCTURED-LOGGING.md

### Enable Debug Logging

```yaml
# src/main/resources/application.yml
logging:
  level:
    com.example: DEBUG
```

### Run Tests

```bash
# Full test suite (72 tests)
mvn test

# Specific area
mvn test -Dtest=RouteRegistryTest      # Routing
mvn test -Dtest=*Storage*Test          # File storage
mvn test -Dtest=ScriptProcessorSecurityTest  # Security
```

---

## Quick Reference

| Issue | Command | Reference |
|-------|---------|-----------|
| Format code | `mvn spotless:apply` | docs/DEVELOPMENT.md |
| Run tests | `mvn test` | docs/DEVELOPMENT.md |
| Check routes | Check `routes.yml` | docs/ROUTE-REGISTRY.md |
| Check memory | `curl localhost:8080/metrics` | docs/MEMORY-GUARANTEE.md |
| View logs | `tail -f logs/application.log` | docs/STRUCTURED-LOGGING.md |
| Clean build | `mvn clean package` | CLAUDE.md |
