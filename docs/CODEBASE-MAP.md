# Codebase Navigation Map

**Last updated**: 2026-03-22

Quick reference for navigating the codebase and understanding component relationships.

## Package Structure

```
src/main/java/com/example/servlet/
├── Main.java                           - Tomcat bootstrap (140 lines)
├── RouterServlet.java                  - HTTP request router (236 lines)
│
├── route/                              - Declarative routing system
│   ├── RouteRegistry.java              - YAML loader & pattern matcher (singleton)
│   ├── Route.java                      - Route model (Lombok @Getter/@Setter)
│   └── RouteDispatcher.java            - Reflection-based handler dispatch
│
├── processor/                          - Request processors (IRequestProcessor)
│   ├── IRequestProcessor.java          - Processor interface
│   ├── ProcessorResponse.java          - Response builder (Lombok @Builder)
│   ├── ScriptProcessor.java            - JavaScript execution (Rhino)
│   │   └── Lines 39-147                - ClassShutter security implementation
│   ├── FileUploadProcessor.java        - Multipart file upload handler
│   ├── TemplateProcessor.java          - HTML template rendering
│   └── ModuleProcessor.java            - JavaScript module CRUD
│
├── storage/                            - Chunked file storage (1MB chunks)
│   ├── AttachmentStorage.java          - Storage strategy interface
│   ├── Attachment.java                 - File metadata model (Lombok)
│   ├── AttachmentManager.java          - Singleton storage coordinator
│   ├── LocalFileSystemStorage.java     - Filesystem storage implementation
│   │   ├── saveMetadata()              - Uses JsonUtil (NOT new Gson()!)
│   │   ├── loadMetadata()              - Uses JsonUtil (NOT new Gson()!)
│   │   ├── ChunkedOutputStream         - Inner class: 1MB write buffer
│   │   └── ChunkedInputStream          - Inner class: 1MB read streaming
│   └── ChunkedOutputStream.java        - Standalone chunked writer
│
├── handler/                            - Singleton HTTP handlers
│   ├── AttachmentHandler.java          - File operations (download/delete/list)
│   └── DataBrowserHandler.java         - Database browser operations
│
├── datasource/                         - Database connection strategies
│   ├── DataSourceStrategy.java         - Strategy interface
│   ├── DataSourceRegistry.java         - Strategy registry (singleton)
│   ├── PostgreSqlStrategy.java         - PostgreSQL connection builder
│   ├── MySqlStrategy.java              - MySQL connection builder
│   └── SnowflakeStrategy.java          - Snowflake connection builder
│
├── module/                             - JavaScript module system
│   ├── Module.java                     - Module metadata model (Lombok)
│   ├── ModuleManager.java              - Module CRUD & filesystem storage
│   └── ModuleDependencyResolver.java   - Dependency graph & cycle detection
│
├── model/                              - Shared domain models (all Lombok)
│   ├── Attachment.java                 - File metadata
│   ├── Module.java                     - Module metadata
│   ├── Route.java                      - Route configuration
│   └── ProcessorResponse.java          - HTTP response builder
│
└── util/                               - Shared utilities
    ├── PropertiesUtil.java             - YAML config loader (env vars)
    ├── JsonUtil.java                   - Gson wrapper with InstantTypeAdapter
    ├── TemplateEngine.java             - HTML template processor
    ├── StructuredLogger.java           - MDC-based structured logging
    └── CorrelationIdFilter.java        - Servlet filter for correlation IDs
```

## Configuration Files

```
src/main/resources/
├── application.yml                     - Main configuration
│   ├── server.port                     - HTTP port (default: 8080)
│   ├── storage.chunkSize               - Chunk size (default: 1MB)
│   ├── script.timeout                  - JS timeout (default: 5000ms)
│   └── script.maxMemory                - JS memory limit (default: 10MB)
│
├── routes.yml                          - All 21 HTTP routes (single source of truth)
│   ├── static routes                   - HTML/CSS/JS files from classpath
│   ├── handler routes                  - Invoke singleton methods via reflection
│   ├── processor routes                - Instantiate RequestProcessor impls
│   └── builtin routes                  - RouterServlet methods (/health, /metrics)
│
└── static/                             - Static web resources
    ├── index.html                      - Home page with endpoint cards
    ├── script-editor.html              - JavaScript IDE
    └── data-browser.html               - Database browser UI
```

## Test Structure

```
src/test/java/com/example/servlet/
├── route/
│   ├── RouteRegistryTest.java          - 10 tests: pattern matching, params, wildcards
│   └── RouteDispatcherTest.java        - 11 tests: all route types, responses
│
├── storage/
│   ├── LocalFileSystemStorageTest.java - Chunking, metadata persistence
│   └── AttachmentManagerTest.java      - Cache management
│
├── processor/
│   └── ScriptProcessorSecurityTest.java - 26 tests: ClassShutter validation
│
├── datasource/
│   ├── DataSourceRegistryTest.java     - Strategy lookup
│   ├── PostgreSqlStrategyTest.java     - URL building, validation
│   ├── MySqlStrategyTest.java          - URL building, validation
│   └── SnowflakeStrategyTest.java      - URL assembly, optional fields
│
└── util/
    ├── TemplateEngineTest.java         - Variable substitution, XSS, loops
    ├── JsonUtilTest.java               - JSON serialization, Instant handling
    └── CorrelationIdFilterTest.java    - MDC population, cleanup
```

**Total**: 72 tests (all passing)

## When Working On...

### Routing Changes
**Files to check**:
- `src/main/resources/routes.yml` - Route definitions
- `route/RouteRegistry.java` - Pattern matching logic
- `route/RouteDispatcher.java` - Handler dispatch logic
- `RouterServlet.java` - Request entry point

**Tests to run**:
```bash
mvn test -Dtest=RouteRegistryTest,RouteDispatcherTest
```

**Reference**: docs/ROUTE-REGISTRY.md

---

### File Upload/Storage
**Files to check**:
- `storage/LocalFileSystemStorage.java` - Chunking implementation
- `storage/AttachmentManager.java` - Metadata cache
- `processor/FileUploadProcessor.java` - Multipart handling
- `handler/AttachmentHandler.java` - Download/delete operations

**Critical**:
- ⚠️ Always use `JsonUtil.toJson()` / `fromJson()` (NOT `new Gson()`)
- ⚠️ Never load entire file into memory
- ⚠️ Update metadata cache when adding/deleting

**Tests to run**:
```bash
mvn test -Dtest=*Storage*Test,AttachmentManagerTest
```

**Reference**: docs/MEMORY-GUARANTEE.md

---

### JavaScript Execution
**Files to check**:
- `processor/ScriptProcessor.java` - Rhino integration
- `processor/ScriptProcessor.java:39-147` - ClassShutter security
- `src/main/resources/application.yml` - Timeout/memory limits

**Security validation**:
```bash
mvn test -Dtest=ScriptProcessorSecurityTest
```

**Reference**: docs/SCRIPT-SECURITY.md

---

### Database Browser
**Files to check**:
- `datasource/*Strategy.java` - Database-specific logic
- `datasource/DataSourceRegistry.java` - Strategy registry
- `handler/DataBrowserHandler.java` - Session management, queries
- `src/main/resources/static/data-browser.html` - UI

**Tests to run**:
```bash
mvn test -Dtest=DataSource*Test
```

**Reference**: docs/data-browser.md

---

### Logging & Observability
**Files to check**:
- `util/StructuredLogger.java` - Structured logging wrapper
- `util/CorrelationIdFilter.java` - MDC population
- `RouterServlet.java` - Request timing, metrics

**View logs**:
```bash
tail -f logs/application.log
grep "correlationId=abc-123" logs/application.log
```

**Reference**: docs/STRUCTURED-LOGGING.md

---

## Component Dependencies

### Request Flow
```
HTTP Request
    ↓
RouterServlet.doGet/doPost/doPut/doDelete
    ↓
CorrelationIdFilter (populates MDC)
    ↓
RouteRegistry.findRoute(method, path)
    ↓
RouteDispatcher.dispatch(match, request, response)
    ↓
[Static File] → classpath resource
[Handler]     → AttachmentHandler / DataBrowserHandler
[Processor]   → ScriptProcessor / FileUploadProcessor / TemplateProcessor
[Builtin]     → RouterServlet.handleHealth / handleMetrics
```

### Storage Flow (Upload)
```
POST /api/upload (multipart/form-data)
    ↓
FileUploadProcessor.process()
    ↓
AttachmentManager.store(attachment, inputStream)
    ↓
LocalFileSystemStorage.store()
    ↓
ChunkedOutputStream.write() (1MB chunks)
    ↓
Disk: attachments/{uuid}/chunk_0, chunk_1, ..., metadata.json
    ↓
AttachmentManager.cache.put(id, attachment)
```

### Storage Flow (Download)
```
GET /api/attachment/{id}/download
    ↓
AttachmentHandler.handleDownload(request, response, id)
    ↓
AttachmentManager.retrieve(id)
    ↓
LocalFileSystemStorage.retrieve()
    ↓
ChunkedInputStream.read() (streams 1MB at a time)
    ↓
HTTP Response (chunked transfer encoding)
```

### Script Execution Flow
```
POST /api/script (application/javascript)
    ↓
ScriptProcessor.process()
    ↓
executeScript(code, params)
    ↓
Rhino Context (optimization level -1, ClassShutter enabled)
    ↓
Script execution (timeout & memory monitoring)
    ↓
JSON response (result, console logs, performance metrics)
```

---

## Key Architectural Patterns

### Strategy Pattern
- **AttachmentStorage**: LocalFileSystemStorage, (future: S3Storage, DatabaseStorage)
- **DataSourceStrategy**: PostgreSqlStrategy, MySqlStrategy, SnowflakeStrategy

### Singleton Pattern
- **RouteRegistry**: Single instance loads routes.yml once
- **AttachmentManager**: Manages metadata cache
- **DataSourceRegistry**: Registry of database strategies
- **ModuleManager**: Manages JavaScript modules
- **AttachmentHandler**: HTTP handler instance
- **DataBrowserHandler**: HTTP handler instance

### Streaming Pattern
- **ChunkedOutputStream**: Write files in 1MB chunks
- **ChunkedInputStream**: Read files in 1MB chunks
- **AttachmentHandler**: Stream responses without buffering

### Builder Pattern
- **ProcessorResponse**: Fluent API for building HTTP responses
- **StructuredLogger**: Fluent API for building log entries with fields

---

## Critical Code Locations

### Security
- `ScriptProcessor.java:39-147` - ClassShutter whitelist/blacklist
- `ScriptProcessor.java:executeScript()` - Timeout & memory enforcement

### Memory Management
- `LocalFileSystemStorage.java:ChunkedOutputStream` - 1MB write buffer
- `LocalFileSystemStorage.java:ChunkedInputStream` - 1MB read streaming
- `storage/ChunkedOutputStream.java` - Standalone chunked writer

### JSON Serialization
- `util/JsonUtil.java` - Custom InstantTypeAdapter (CRITICAL!)
- `LocalFileSystemStorage.java:saveMetadata()` - Uses JsonUtil
- `LocalFileSystemStorage.java:loadMetadata()` - Uses JsonUtil

### Routing
- `src/main/resources/routes.yml` - All 21 routes
- `route/RouteRegistry.java:findRoute()` - Pattern matching
- `route/RouteDispatcher.java:dispatch()` - Handler invocation

---

## Quick Commands

```bash
# Build & Run
mvn clean package                       # Production build
mvn -PappRun                           # Development run
java -jar target/servlet-example.jar   # Run JAR

# Testing
mvn test                               # All tests (72)
mvn test -Dtest=ClassName              # Specific test
mvn spotless:check                     # Format check
mvn spotless:apply                     # Auto-format

# Monitoring
curl http://localhost:8080/health      # Health check
curl http://localhost:8080/metrics     # System metrics
tail -f logs/application.log           # View logs

# Interactive Tools
open http://localhost:8080/            # Home page
open http://localhost:8080/script-editor     # JavaScript IDE
open http://localhost:8080/data-browser      # Database browser
```

---

## Documentation Index

| Topic | Document | Key Files |
|-------|----------|-----------|
| Overview | CLAUDE.md | All packages |
| Routing System | docs/ROUTE-REGISTRY.md | route/, routes.yml |
| Memory Management | docs/MEMORY-GUARANTEE.md | storage/ |
| Script Security | docs/SCRIPT-SECURITY.md | ScriptProcessor.java:39-147 |
| Database Browser | docs/data-browser.md | datasource/, DataBrowserHandler.java |
| Logging | docs/STRUCTURED-LOGGING.md | util/StructuredLogger.java |
| Development | docs/DEVELOPMENT.md | pom.xml, .git/hooks/pre-commit |
| Troubleshooting | docs/TROUBLESHOOTING.md | - |
| Navigation | docs/CODEBASE-MAP.md | This file |
