# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run Commands

### Development Mode
```bash
# Quick run during development (similar to bootRun)
mvn -PappRun

# Alternative development run
mvn compile exec:java

# Override server port
SERVER_PORT=9090 mvn -PappRun
```

### Production Build
```bash
# Build executable JAR
mvn clean package

# Run the JAR
java -jar target/servlet-example.jar

# Run with custom port
SERVER_PORT=9090 java -jar target/servlet-example.jar
```

## Configuration System

Configuration is managed through `src/main/resources/application.yml` with environment variable support using `${ENV_VAR:defaultValue}` syntax. The `PropertiesUtil` class handles YAML loading and placeholder resolution at runtime.

Environment variables always take precedence over YAML defaults. Common overrides:
- `SERVER_PORT` - HTTP server port (default: 8080)
- `STORAGE_TYPE` - Storage type for attachments (default: filesystem)
- `STORAGE_CHUNK_SIZE` - Chunk size for file streaming (default: 1MB)
- `STORAGE_PATH` - Attachment storage directory (default: attachments)

## Core Architecture

### Declarative Routing via routes.yml

The application uses **fully declarative routing** through a YAML configuration file:

1. **RouterServlet** receives all HTTP requests
2. **RouteRegistry** (singleton) loads all routes from `routes.yml` at startup
3. **RouteDispatcher** dispatches requests based on route type (static, handler, processor, builtin)
4. Returns appropriate response (HTML, JSON, files, etc.)

**Architecture Flow:**
```
HTTP Request → RouterServlet
              ↓
    RouteRegistry.findRoute(method, path)
              ↓
         RouteMatch found?
          ↓           ↓
        YES          NO
          ↓           ↓
   RouteDispatcher   404 JSON
          ↓
    Switch on type:
    - static → serve HTML/JS/CSS files
    - handler → invoke handler methods via reflection
    - processor → instantiate and call RequestProcessor
    - builtin → call RouterServlet methods (/health, /metrics)
```

**Key Components:**
- **routes.yml** - Single source of truth for all 21 routes
- **RouteRegistry** - Loads routes, performs pattern matching (wildcards, path params)
- **RouteDispatcher** - Dispatches to appropriate handler based on route type
- **RouterServlet** - Thin wrapper (236 lines) handling request timing and logging

#### Adding New Routes

To add a new route, simply edit `routes.yml`:

```yaml
- path: /api/myendpoint
  method: POST
  type: processor
  processor: MyCustomProcessor
  contentType: application/json
  description: My custom endpoint
```

Then add the processor class to `RouteDispatcher.getProcessorInstance()`:

```java
case "MyCustomProcessor":
  return new MyCustomProcessor();
```

No changes needed to RouterServlet!

### JavaScript Execution Engine (Rhino)

The `ScriptProcessor` provides server-side JavaScript execution using Mozilla Rhino:

- **Request format**: POST to `/api/script` with `Content-Type: application/javascript`
- **Body structure**: `{"script": "...code...", "params": {...}}`
- **Sandbox features**:
  - Optimization level -1 (interpreted mode for security)
  - Configurable timeout via `script.timeout` (default: 5000ms)
  - Memory limit enforcement via `script.maxMemory` (default: 10MB)
  - Exposes server context as `request` object (method, path, remoteAddr, queryParams)
  - Provides `console.log()` that captures output to response
  - Automatic type conversion between JavaScript and Java objects
- **Performance Monitoring**:
  - Execution time tracking in milliseconds (`executionTimeMs`)
  - Memory usage tracking in bytes (`memoryUsedBytes`)
  - Metrics included in every successful response

#### Java Class Access Security

The script sandbox uses a **hybrid whitelist/blacklist approach** (`ScriptProcessor.java:27-140`) to control Java class access:

**Whitelist - Explicitly Allowed Classes:**
- Collections: `ArrayList`, `HashMap`, `HashSet`, `LinkedList`, `TreeMap`, `TreeSet`, `LinkedHashMap`, `LinkedHashSet`, `Vector`, `Stack`
- Utilities: `Date`, `UUID`, `Optional`, `Arrays`, `Collections`
- Primitives/Wrappers: `String`, `StringBuilder`, `StringBuffer`, `Math`, `Integer`, `Long`, `Double`, `Float`, `Boolean`, `Character`, `Byte`, `Short`
- Date/Time (Java 8+): `LocalDate`, `LocalDateTime`, `LocalTime`, `Instant`, `Duration`, `Period`, `ZonedDateTime`, `ZoneId`
- Math: `BigDecimal`, `BigInteger`

**Blacklist - Explicitly Blocked:**
- Classes: `System`, `Runtime`, `ProcessBuilder`, `Process`, `ClassLoader`, `Thread`, `ThreadGroup`, `SecurityManager`
- Package prefixes: `java.io.*` (file system), `java.nio.file.*` (file system), `java.net.*` (network), `java.lang.reflect.*` (reflection), `java.lang.invoke.*` (method handles), `javax.script.*` (script engines), `sun.*`, `com.sun.*`, `jdk.*` (internal classes), `java.security.*`, `javax.naming.*` (JNDI), `javax.management.*` (JMX), `java.sql.*`, `javax.sql.*` (database)

**Default Policy:**
- Allow other `java.util.*` and `java.lang.*` classes not explicitly blacklisted
- Block everything else by default

**Example Usage:**
```javascript
// Allowed - Collections
var list = new java.util.ArrayList();
list.add("Hello");
list.add(42);
var item = list.get(0);

var map = new java.util.HashMap();
map.put("key", "value");
var val = map.get("key");

// Blocked - System access
java.lang.System.exit(0);  // ERROR: blocked

// Blocked - File I/O
new java.io.File("/etc/passwd");  // ERROR: blocked

// Blocked - Network
new java.net.Socket("evil.com", 1337);  // ERROR: blocked
```

JavaScript execution occurs in `ScriptProcessor.process()` which:
1. Captures memory state and start time before execution
2. Calls `executeScript()` which creates isolated Rhino context
3. Injects parameters and server context into scope
4. Adds utility functions (JSON, console)
5. Executes script with timeout and memory monitoring
6. Calculates execution metrics (time and memory delta)
7. Returns response with result, console logs, and performance metrics

**Response format**:
```json
{
  "status": "success",
  "data": {
    "result": <script_return_value>,
    "console": ["log messages"],
    "executionTimeMs": 3,
    "memoryUsedBytes": 1024
  },
  "timestamp": 1234567890
}
```

### Template Engine

Custom template engine in `TemplateEngine.java` supports:

- **Variable substitution**: `{{variableName}}` with dot notation (`{{user.name}}`)
- **For loops**: `{{#for item in items}}...{{/for}}`
- **XSS protection**: Automatic HTML escaping via `escapeHtml()`
- **Template loading**: From classpath resources using `loadTemplate(path)`

Templates are processed in two passes:
1. Process for loops (expands collections into repeated HTML)
2. Process variables (substitutes values with escaping)

The `TemplateProcessor` handles HTTP requests with `Content-Type: text/html`, expecting JSON body with `{"template": "...", "data": {...}}`.

### Chunked File Storage (Memory-Efficient)

The application uses **chunked storage** inspired by ServiceNow Glide to handle large files without memory issues.

**Problem**: Traditional file upload loads entire file into memory (500MB file = 500MB heap usage = OutOfMemoryError)

**Solution**: Files are split into 1MB chunks and processed incrementally.

#### Storage Architecture

**Strategy Pattern** (`AttachmentStorage` interface):
- `LocalFileSystemStorage` - Default, stores chunks as separate files
- `DatabaseStorage` - Future: stores chunks in `attachment` and `attachment_data` tables
- `S3Storage` - Future: stores in AWS S3 with deduplication

#### How It Works

**Upload Flow** (`FileUploadProcessor.java`):
```
Client uploads 500MB file
    ↓
InputStream from HTTP request (no buffering)
    ↓
ChunkedOutputStream (1MB buffer)
    ├─ write chunk 0 → disk (1MB)
    ├─ reuse buffer
    ├─ write chunk 1 → disk (1MB)
    └─ repeat 500 times

Max heap: 1MB (not 500MB!)
Chunks created: 500
```

**Download Flow** (`AttachmentHandler.java`):
```
Client requests GET /api/attachment/{id}/download
    ↓
ChunkedInputStream
    ├─ read chunk 0 from disk (1MB)
    ├─ stream to HTTP response
    ├─ release chunk 0
    ├─ read chunk 1 from disk (1MB)
    └─ repeat

Max heap: 1MB (one chunk at a time)
```

#### Memory Guarantees

**With 1GB heap limit**:
- Upload 500MB file: 1MB heap usage ✅
- Download 500MB file: 1MB heap usage ✅
- 100 concurrent uploads: 100MB heap ✅
- 1000 concurrent uploads: 1GB heap ✅ (at limit)

**Key**: Memory scales with concurrent requests, NOT file sizes!

#### Directory Structure

```
attachments/
  {uuid}/
    chunk_0  (1MB)
    chunk_1  (1MB)
    chunk_2  (1MB)
    ...
    metadata.json  (attachment metadata)
```

**Metadata Persistence (Implementation Details):**
- Each attachment's metadata is saved as `metadata.json` in its directory
- Contains: id, fileName, contentType, sizeBytes, hash, storageType, storagePath, createdAt, updatedAt
- **CRITICAL**: Must use `JsonUtil.toJson()` / `JsonUtil.fromJson()` for all JSON serialization
  - `JsonUtil` has custom `InstantTypeAdapter` registered for `java.time.Instant` fields
  - Using `new Gson()` directly will cause `JsonIOException` on Instant serialization
  - This bug was fixed in commit 5e6eabd - LocalFileSystemStorage.saveMetadata() and loadMetadata()
- Metadata cache: `ConcurrentHashMap<String, Attachment>` in `AttachmentManager`
  - Loaded on startup via `loadAllMetadata()` which scans attachments directory
  - Updated in-memory on each upload/delete operation
  - Thread-safe for concurrent access
- Concurrency safety:
  - `ConcurrentHashMap` for cache (thread-safe reads/writes)
  - Unique UUIDs prevent upload conflicts
  - Attachments are immutable (never updated after creation)
  - `Files.writeString()` provides atomic writes for metadata.json

#### Configuration

```yaml
storage:
  type: filesystem              # or s3, database
  chunkSize: 1048576           # 1MB chunks
  filesystem:
    path: attachments

upload:
  maxFileSize: 524288000       # 500MB
  maxRequestSize: 1073741824   # 1GB
```

#### API Endpoints (Routing & Implementation)

All routes defined in `routes.yml` and dispatched via `RouteDispatcher`:

- `POST /api/upload` → `FileUploadProcessor` (via routes.yml)
  - Route type: `processor`, processor: `FileUploadProcessor`
  - Delegates to `AttachmentManager.store()` for chunked storage
  - Returns attachment ID, hash, download URL in response

- `GET /api/attachments` → `AttachmentHandler.handleList()` (via routes.yml)
  - Route type: `handler`, handler: `AttachmentHandler`, method: `handleList`
  - Returns all attachments from in-memory cache

- `GET /api/attachment/{id}/download` → `AttachmentHandler.handleDownload()`
  - Route type: `handler` with path parameter `{id}`
  - Streams via `ChunkedInputStream` (inner class in `LocalFileSystemStorage`)
  - Sets Content-Disposition header for file download

- `GET /api/attachment/{id}` → `AttachmentHandler.handleMetadata()`
  - Returns metadata from cache or loads from metadata.json

- `DELETE /api/attachment/{id}` → `AttachmentHandler.handleDelete()`
  - Deletes directory recursively via `LocalFileSystemStorage.delete()`
  - Removes from cache via `AttachmentManager.delete()`

**Key Classes & Their Roles:**
- `AttachmentManager` - Singleton coordinating storage operations, manages metadata cache
- `LocalFileSystemStorage` - Implements `AttachmentStorage`, handles chunk I/O and metadata persistence
- `ChunkedOutputStream` - Buffers and writes 1MB chunks during upload
- `ChunkedInputStream` - Reads and streams chunks during download (inner class in LocalFileSystemStorage)
- `AttachmentHandler` - HTTP handler for download/delete/list operations (invoked via RouteDispatcher)
- `FileUploadProcessor` - Processes multipart uploads, delegates to AttachmentManager (instantiated by RouteDispatcher)

**Common Pitfalls to Avoid:**
1. ❌ Never use `new Gson()` directly - always use `JsonUtil.toJson()` / `fromJson()`
2. ❌ Don't load entire file into memory - use streaming APIs
3. ❌ Don't modify Attachment objects after creation (they're immutable)
4. ❌ Don't forget to update metadata cache when adding/deleting attachments
5. ❌ Don't use `response.getWriter()` after `response.getOutputStream()` (or vice versa)
   - Static files and downloads use OutputStream, JSON responses use Writer

**Testing Considerations:**
- Test large files (100MB+) to verify chunking works
- Verify SHA-256 hash matches after download
- Test concurrent uploads (ensure no file conflicts)
- Verify metadata persists across server restarts
- Test deletion removes both files and cache entry

See `docs/MEMORY-GUARANTEE.md` for detailed memory analysis.

### Route Registry System

**Core routing mechanism** - All 21 application routes defined in YAML.

**Location**: `src/main/resources/routes.yml`

**Key Components**:
- `RouteRegistry` - Loads routes.yml at startup, performs pattern matching with wildcards and path parameters
- `Route` - Lombok model representing route configuration (path, method, type, handler, etc.)
- `RouteDispatcher` - Dispatches requests to appropriate handlers via reflection

**Benefits**:
- ✅ Single source of truth - all routes in one file
- ✅ Add/modify routes by editing YAML (no Java code changes)
- ✅ Automatic path parameter extraction (`{id}` → captured value)
- ✅ Wildcard support (`/api/modules/**`)
- ✅ Minimal servlet code (236 lines, 49% reduction from 461 lines)
- ✅ No ProcessorRegistry or content-type matching needed

**Route Types**:
1. `static` - Serve static files from classpath (HTML, CSS, JS)
2. `handler` - Invoke singleton handler methods via reflection (AttachmentHandler, DataBrowserHandler)
3. `processor` - Instantiate and call IRequestProcessor implementations (all 4 processors)
4. `builtin` - Built-in RouterServlet methods (handleHealth, handleMetrics)

**Example Routes**:
```yaml
# Static file
- path: /
  method: GET
  type: static
  resource: static/index.html
  contentType: text/html

# Handler with path parameter
- path: /api/attachment/{id}/download
  method: GET
  type: handler
  handler: AttachmentHandler
  handlerMethod: handleDownload
  pathParams: [id]

# Processor
- path: /api/json
  method: POST
  type: processor
  processor: JsonDataProcessor
  contentType: application/json
```

**RouterServlet Integration** (fully integrated):
```java
// All HTTP methods use single dispatch point
@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  handleRequest(request, response, () -> dispatchOrNotFound(request, response));
}

private void dispatchOrNotFound(HttpServletRequest request, HttpServletResponse response) {
  RouteRegistry.RouteMatch match = RouteRegistry.getInstance().findRoute(method, path);
  if (match != null && routeDispatcher.dispatch(match, request, response)) {
    return; // Handled
  }
  // Handle builtin routes or return 404
}
```

**Testing**:
- `RouteRegistryTest.java` - 10 tests covering pattern matching, parameters, wildcards
- `RouteDispatcherTest.java` - 11 tests verifying proper response handling for all route types
- All 72 tests passing

See `docs/ROUTE-REGISTRY.md` for detailed documentation.

### Embedded Tomcat Setup

`Main.java` bootstraps embedded Tomcat:
- Configures servlet context and adds `RouterServlet` with `/*` mapping
- Applies `MultipartConfigElement` for file upload support (configured via YAML)
- Tomcat starts synchronously and blocks with `await()` for graceful shutdown

File upload limits are enforced at Tomcat level via multipart config (maxFileSize, maxRequestSize, fileSizeThreshold).

## Project Structure

- **Main.java**: Embedded Tomcat bootstrap and server configuration
- **RouterServlet.java**: Minimal servlet (236 lines) - request timing, logging, and dispatch
- **route/**: Declarative routing system
  - `RouteRegistry` - Loads routes.yml at startup, performs pattern matching
  - `Route` - Lombok model for route configuration (path, method, type, handler)
  - `RouteDispatcher` - Dispatches to handlers via reflection based on route type
  - `routes.yml` - YAML config defining all 21 application routes (single source of truth)
- **processor/**: Request processor implementations
  - `IRequestProcessor` - Interface defining processor contract (supports, process, getContentType)
  - `ProcessorResponse` - Lombok builder for HTTP responses
  - Individual processors: FileUpload, Script, Template, Module
- **storage/**: Chunked file storage with strategy pattern
  - `AttachmentStorage` - Interface for storage strategies
  - `Attachment` - Lombok model for file metadata
  - `AttachmentManager` - Singleton storage manager with in-memory cache
  - `LocalFileSystemStorage` - Filesystem storage with 1MB chunking
  - `ChunkedOutputStream` - Memory-efficient write stream (1MB buffer)
  - `ChunkedInputStream` - Memory-efficient read stream (1MB chunks)
- **handler/**: Singleton HTTP request handlers (invoked via RouteDispatcher)
  - `AttachmentHandler` - File download/delete/list operations with streaming
  - `DataBrowserHandler` - Database browser operations (connect, query, tables)
- **module/**: JavaScript module system
  - `Module` - Lombok model for module metadata
  - `ModuleManager` - Module CRUD and filesystem storage
  - `ModuleDependencyResolver` - Dependency resolution with cycle detection
- **model/**: Shared Lombok models
  - `Attachment`, `Module`, `Route`, `ProcessorResponse` - All use Lombok for boilerplate reduction
- **util/**: Shared utilities
  - `PropertiesUtil` - YAML config loader with environment variable interpolation
  - `JsonUtil` - Gson wrapper with custom Instant TypeAdapter (CRITICAL: must use for Instant serialization)
  - `TemplateEngine` - HTML template rendering with variable substitution and for loops

## Feature Documentation

- **Data Browser** (`docs/data-browser.md`) — Web-based database browser at `/data-browser`. Supports PostgreSQL, MySQL, and Snowflake with on-demand JDBC driver downloads, session management, table browsing, and SQL query execution.

## Testing Endpoints

All 21 endpoints defined in `routes.yml`. Visit **http://localhost:8080/** for a beautiful home page with links to all endpoints.

**Interactive Tools:**
- `/` - Beautiful HTML home page with organized endpoint cards
- `/script-editor` - JavaScript IDE with Rhino engine and performance metrics
- `/data-browser` - Database browser (PostgreSQL, MySQL, Snowflake)

**Monitoring:**
- `/health` - Health check with uptime (JSON)
- `/metrics` - System metrics (memory, threads, request count)

**API Endpoints:**
- `/api/upload` (POST) - File uploads with chunked storage (multipart/form-data)
- `/api/script` (POST) - Execute JavaScript server-side (application/javascript)
- `/api/render` (POST) - Render HTML templates (text/html)
- `/api/attachments` (GET) - List uploaded files
- `/api/attachment/{id}` (GET/DELETE) - Get metadata or delete attachment
- `/api/attachment/{id}/download` (GET) - Download file with streaming
- `/api/modules/**` (GET/POST/PUT/DELETE) - JavaScript module CRUD
- `/api/data-browser/*` (POST) - Database operations

Use curl examples from README.md or the interactive tools at `/script-editor` and `/data-browser`.

### Interactive Script Editor

The script editor (`/script-editor`) is a web-based JavaScript IDE that:
- Displays performance metrics after each execution (execution time and memory usage)
- Shows metrics in a dedicated "Performance Metrics" panel with formatted values
- Formats execution time: displays in ms (< 1s) or seconds (≥ 1s)
- Formats memory usage: displays in B, KB, or MB based on size
- Includes pre-loaded examples demonstrating Java interoperability
- Captures and displays console.log() output separately from results
- Supports keyboard shortcut (Ctrl+Enter / Cmd+Enter) for quick execution

The editor receives performance data from the ScriptProcessor response and renders it in the output panel alongside the script result and console logs.
