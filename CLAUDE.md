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

## Core Architecture

### Request Processing Strategy Pattern

The application uses a **Strategy + Registry pattern** for content-type based routing:

1. **RouterServlet** (`RouterServlet.java:115`) receives all HTTP requests
2. Extracts the `Content-Type` header from the request
3. **ProcessorRegistry** (singleton) looks up the appropriate processor
4. Each **RequestProcessor** implementation handles a specific content type
5. Returns a **ProcessorResponse** with status code, body, and headers

#### Processor Registration Flow

Processors are registered during servlet initialization in `RouterServlet.init()`:
```java
registry.register(new FormDataProcessor());       // application/x-www-form-urlencoded
registry.register(new JsonDataProcessor());        // application/json
registry.register(new FileUploadProcessor());      // multipart/form-data
registry.register(new ScriptProcessor());          // application/javascript
registry.register(new TemplateProcessor());        // text/html
```

The `ProcessorRegistry` uses two lookup strategies:
1. Direct lookup by normalized content type (e.g., "application/json")
2. Fallback to `processor.supports(contentType)` for variants like "application/json; charset=UTF-8"

#### Adding New Processors

To add support for a new content type:

1. Create a class implementing `RequestProcessor` interface (3 methods: `supports()`, `process()`, `getContentType()`)
2. Register it in `RouterServlet.init()`: `registry.register(new YourProcessor())`
3. Add the route path to `RouterServlet.doPost()` or `doGet()` switch statement
4. The processor automatically handles all Content-Type matching via the registry

Example: To add XML support, implement `XmlDataProcessor`, register it in `init()`, and add `/api/xml` to the routing switch.

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

### Embedded Tomcat Setup

`Main.java` bootstraps embedded Tomcat:
- Configures servlet context and adds `RouterServlet` with `/*` mapping
- Applies `MultipartConfigElement` for file upload support (configured via YAML)
- Tomcat starts synchronously and blocks with `await()` for graceful shutdown

File upload limits are enforced at Tomcat level via multipart config (maxFileSize, maxRequestSize, fileSizeThreshold).

## Project Structure

- **Main.java**: Embedded Tomcat bootstrap and server configuration
- **RouterServlet.java**: Central servlet handling all HTTP routing
- **processor/**: Strategy pattern implementations for different content types
  - `RequestProcessor` - Interface defining processor contract
  - `ProcessorRegistry` - Singleton registry for processor lookup
  - `ProcessorResponse` - Builder pattern for processor responses
  - Individual processors: Form, JSON, FileUpload, Script, Template
- **util/**: Shared utilities
  - `PropertiesUtil` - YAML config loader with environment variable interpolation
  - `JsonUtil` - Gson wrapper for JSON serialization
  - `TemplateEngine` - HTML template rendering with variable substitution and loops

## Testing Endpoints

The application exposes these endpoints (see `Main.java:50-64` for complete list):

**GET endpoints:**
- `/` - Welcome message with API documentation
- `/health` - Health check with uptime
- `/metrics` - System metrics (memory, threads, request count)
- `/script-editor` - Interactive JavaScript code editor (static HTML)

**POST endpoints:**
- `/api/form` - Form data (application/x-www-form-urlencoded)
- `/api/json` - JSON payloads (application/json)
- `/api/upload` - File uploads (multipart/form-data)
- `/api/script` - JavaScript execution (application/javascript)
- `/api/render` - Template rendering (text/html)

Use curl examples from README.md or the interactive script editor at `/script-editor`.

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
