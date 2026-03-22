# Development Guide

**Last updated**: 2026-03-22

Quick reference for development workflows, tools, and best practices.

## ✅ Definition of Done

Use this checklist to verify your changes are complete before committing.

### For Any Change

- [ ] **Code compiles**: `mvn clean package` succeeds
- [ ] **Tests pass**: `mvn test` succeeds (or relevant subset)
- [ ] **Code formatted**: `mvn spotless:check` passes (or run `mvn spotless:apply`)
- [ ] **No compilation warnings**: Check `mvn compile` output
- [ ] **Manual test passed**: If user-facing, test with curl/browser
- [ ] **Documentation updated**: If behavior changed, update relevant .md files
- [ ] **No debug code**: No `System.out.println`, commented code, or TODO without JIRA

### For Critical Areas

**If modifying `storage/` (file storage):**
- [ ] All above +
- [ ] Uses streaming (no `byte[]` for file content, no `Files.readAllBytes`)
- [ ] Uses `JsonUtil` NOT `new Gson()` for metadata
- [ ] Storage tests pass: `mvn test -Dtest=*Storage*Test`
- [ ] Memory test: Upload 100MB file, verify `curl localhost:8080/metrics` shows < 10MB heap
- [ ] Integration test: Upload and download file, verify with `diff` or `sha256sum`

**If modifying `route/` or `routes.yml` (routing):**
- [ ] All above +
- [ ] YAML syntax valid (no tabs, proper indentation)
- [ ] Processor/Handler registered in `RouteDispatcher`
- [ ] Route order correct (specific routes before wildcards)
- [ ] Route tests pass: `mvn test -Dtest=Route*Test`
- [ ] Manual test: `curl http://localhost:8080/your-route` returns expected response

**If modifying `processor/ScriptProcessor.java` (security):**
- [ ] All above +
- [ ] All 26 security tests pass: `mvn test -Dtest=ScriptProcessorSecurityTest`
- [ ] No classes added to whitelist without security review
- [ ] Timeout and memory limits still enforced
- [ ] Manual test: Try `java.lang.System.exit()` → should be blocked
- [ ] Documentation updated: Add to docs/SCRIPT-SECURITY.md if whitelist changed

**If modifying `datasource/` (database support):**
- [ ] All above +
- [ ] Strategy implements all interface methods
- [ ] Strategy registered in `DataSourceRegistry` constructor
- [ ] DataSource tests pass: `mvn test -Dtest=DataSource*Test`
- [ ] Manual test: Connect to database via data-browser UI

### Ready to Commit?

**Final checks:**
```bash
# 1. Full test suite
mvn clean test

# 2. Package successfully
mvn clean package

# 3. Spotless check
mvn spotless:check

# 4. Git status clean (no unintended files)
git status

# 5. Manual smoke test (if applicable)
mvn -PappRun
curl http://localhost:8080/health  # Should return {"status":"healthy"}
```

**All green?** ✅ Commit with descriptive message following [Conventional Commits](https://www.conventionalcommits.org/):
- `feat:` for new features
- `fix:` for bug fixes
- `refactor:` for code improvements
- `docs:` for documentation
- `test:` for test changes

---

## Development Workflows

### TDD Loop (Test-Driven Development)

```bash
# 1. Write failing test
mvn test -Dtest=MyNewTest

# 2. Implement feature
# ... code ...

# 3. Run test again
mvn test -Dtest=MyNewTest

# 4. Refactor if needed

# 5. Run full suite
mvn test
```

### Feature Addition Workflow

```bash
# 1. Create feature branch
git checkout -b feature/my-feature

# 2. Add route to routes.yml
# ... edit routes.yml ...

# 3. Create processor/handler
# ... create MyProcessor.java ...

# 4. Register in RouteDispatcher
# ... edit RouteDispatcher.java ...

# 5. Write tests
# ... create MyProcessorTest.java ...

# 6. Run tests
mvn test

# 7. Format code (or pre-commit hook does it)
mvn spotless:apply

# 8. Commit
git commit -m "feat: add my feature"
```

### Bug Fix Workflow

```bash
# 1. Reproduce bug with test
# ... create FailingTest.java ...

# 2. Fix bug
# ... edit code ...

# 3. Verify fix
mvn test -Dtest=FailingTest

# 4. Run full suite (ensure no regressions)
mvn test

# 5. Commit
git commit -m "fix: resolve issue with X"
```

## Debugging

### Enable Debug Logging

```yaml
# src/main/resources/application.yml
logging:
  level:
    com.example: DEBUG
    org.apache.catalina: INFO
```

### Remote Debugging

```bash
# Start with debug port
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
  -jar target/servlet-example.jar

# Connect IDE debugger to localhost:5005
```

### Useful Breakpoints

- `RouteRegistry.java:findRoute()` - Route matching
- `RouteDispatcher.java:dispatch()` - Handler dispatch
- `LocalFileSystemStorage.java:store()` - File upload
- `ScriptProcessor.java:executeScript()` - JavaScript execution

### View Logs

```bash
# Tail logs
tail -f logs/application.log

# Search by correlation ID
grep "correlationId=abc-123" logs/application.log

# Find slow requests (> 1000ms)
grep "responseTimeMs=" logs/application.log | awk -F'responseTimeMs=' '{print $2}' | awk '{if ($1 > 1000) print}'
```

## Code Formatting

This project uses **Spotless** with **Google Java Format** to maintain consistent code style.

### Automatic Formatting

A pre-commit hook automatically runs Spotless before each commit:

```bash
# Pre-commit hook runs automatically
git commit -m "your message"

# If formatting issues found:
# 1. Spotless automatically formats code
# 2. Re-stages formatted files
# 3. Asks you to review and commit again
```

### Manual Formatting

```bash
# Check if code is formatted
mvn spotless:check

# Apply formatting to all files
mvn spotless:apply
```

### Code Style

- **Format**: Google Java Format
- **Indentation**: 2 spaces
- **Line length**: 100 characters (Google default)
- **Import ordering**: Automatic

### Pre-commit Hook Location

`.git/hooks/pre-commit` (not committed to repo)

To install the hook on a new clone:
```bash
chmod +x .git/hooks/pre-commit
```

Or copy from project if provided:
```bash
cp hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

## Lombok

This project uses **Lombok** to reduce boilerplate code.

### Enabled Annotations

- `@Getter` / `@Setter` - Generate getters/setters
- `@Builder` - Generate builder pattern
- `@NoArgsConstructor` - Generate no-args constructor

### Model Classes

All model classes are in `com.example.servlet.model`:

- `Attachment` - File attachment metadata
- `Module` - JavaScript module metadata
- `Route` - Routing configuration
- `ProcessorResponse` - HTTP response builder

Example:
```java
@Getter
@Setter
public class MyModel {
    private String name;
    private int value;

    // No need to write getters/setters!
}
```

### IDE Setup

**IntelliJ IDEA**:
1. Install Lombok plugin: `Preferences → Plugins → Lombok`
2. Enable annotation processing: `Preferences → Build → Compiler → Annotation Processors`

**VS Code**:
1. Install "Language Support for Java" extension
2. Lombok is automatically supported

**Eclipse**:
1. Download lombok.jar from https://projectlombok.org/download
2. Run `java -jar lombok.jar`
3. Select Eclipse installation directory

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RouteRegistryTest

# Run with coverage
mvn clean test jacoco:report
```

## Building

```bash
# Development mode (quick run)
mvn -PappRun

# Production build
mvn clean package

# Run JAR
java -jar target/servlet-example.jar
```

## Code Quality

### Spotless (enforced via pre-commit hook)
- Automatic formatting
- Google Java Format style
- Runs before every commit

### Best Practices
- Use Lombok for models
- Keep methods small and focused
- Write tests for new features
- Follow Google Java Style Guide

## Git Workflow

```bash
# Make changes
git add .

# Commit (Spotless runs automatically)
git commit -m "your message"

# If formatting needed:
# - Spotless auto-formats
# - Files re-staged
# - Review changes
# - Commit again

# Push
git push
```

## Performance Profiling

### Memory Profiling

```bash
# Run with heap dump on OOM
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=heapdump.hprof \
  -jar target/servlet-example.jar

# Check memory usage
curl http://localhost:8080/metrics | jq '.metrics.memory'

# Monitor with jconsole
jconsole <pid>
```

### CPU Profiling

```bash
# Enable flight recorder
java -XX:+UnlockCommercialFeatures -XX:+FlightRecorder \
  -XX:StartFlightRecording=duration=60s,filename=recording.jfr \
  -jar target/servlet-example.jar

# Analyze with jmc
jmc recording.jfr
```

### Request Profiling

```bash
# Check slow requests in logs
grep "responseTimeMs=" logs/application.log | \
  awk -F'responseTimeMs=' '{print $2}' | \
  awk '{print $1}' | sort -n | tail -10
```

## Adding Dependencies

```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>my-library</artifactId>
    <version>1.0.0</version>
</dependency>
```

```bash
# Update dependencies
mvn clean install

# Check for updates
mvn versions:display-dependency-updates

# Check for vulnerabilities
mvn dependency-check:check
```

## Common Tasks

### Change Server Port

```bash
# Environment variable
SERVER_PORT=9090 mvn -PappRun

# Or edit application.yml
server:
  port: 9090
```

### Add New Route

1. Edit `src/main/resources/routes.yml`
2. Register processor in `RouteDispatcher.getProcessorInstance()`
3. Write tests
4. Run `mvn test`

**See**: docs/ROUTE-REGISTRY.md

### Add New Database Type

1. Create `MyDatabaseStrategy.java` implementing `DataSourceStrategy`
2. Register in `DataSourceRegistry` constructor
3. Add connection fields to `DB_FIELDS` in `data-browser.html`
4. Write tests
5. Run `mvn test -Dtest=DataSource*Test`

**See**: docs/data-browser.md

### Modify Chunk Size

```yaml
# src/main/resources/application.yml
storage:
  chunkSize: 2097152  # 2MB instead of 1MB
```

**Note**: Affects memory usage. 2MB chunks = 2MB buffer per concurrent upload.

### Clean Build Directory

```bash
mvn clean
rm -rf target/
rm -rf attachments/  # If needed
rm -rf extlib/       # If needed
```

## Troubleshooting

### "Spotless check failed"
```bash
mvn spotless:apply
git add .
git commit -m "your message"
```

### "Lombok not working in IDE"
**IntelliJ**: Install Lombok plugin + enable annotation processing
**VSCode**: Install "Language Support for Java"
**Eclipse**: Download lombok.jar, run `java -jar lombok.jar`

### "Pre-commit hook not running"
```bash
chmod +x .git/hooks/pre-commit
```

### "Port 8080 already in use"
```bash
lsof -ti:8080 | xargs kill -9
# Or use different port
SERVER_PORT=9090 mvn -PappRun
```

### "Tests failing"
```bash
# Clean build
mvn clean test

# Run specific test with debug
mvn test -Dtest=FailingTest -X

# Skip tests temporarily (not recommended)
mvn package -DskipTests
```

### "JsonIOException with Instant fields"
**Always use `JsonUtil.toJson()` / `fromJson()`, NEVER `new Gson()`**

See: docs/TROUBLESHOOTING.md

## IDE Setup

### IntelliJ IDEA
1. Import as Maven project
2. Install Lombok plugin
3. Enable annotation processing
4. Set Java 17+ SDK
5. Install Google Java Format plugin (optional)

### VS Code
1. Install "Language Support for Java"
2. Install "Maven for Java"
3. Open workspace
4. Extensions auto-configure

### Eclipse
1. Import as Maven project
2. Install Lombok (run lombok.jar installer)
3. Install Spotless Eclipse plugin (optional)
