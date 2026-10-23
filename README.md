# Jakarta EE Servlet Example

A modern web application using Jakarta EE Servlets with **declarative YAML routing**, packaged as an executable JAR with embedded Apache Tomcat.

## Features

- **Beautiful Home Page**: Visit `/` for an elegant HTML dashboard with links to all endpoints
- **Declarative Routing**: All 41 routes configured in `routes.yml` - no hardcoded routing logic
- **File Upload & Storage**: Upload files up to 500MB with automatic chunked storage (1MB chunks), SHA-256 hashing, and streaming downloads — stored on local filesystem or PostgreSQL
- **DB-Backed Properties**: App configuration stored in PostgreSQL `app_properties` table with a three-level lookup: LRU cache → DB → YAML fallback
- **Automatic DB Migrations**: Flyway runs pending `db/V*.sql` migrations on startup
- **Properties REST API**: CRUD endpoints at `/api/properties/**` backed by jOOQ + PostgreSQL
- **Server-Side JavaScript**: Execute JavaScript code using Rhino engine with console output and performance metrics
- **JavaScript Modules**: Create reusable modules with ES6 imports and CommonJS exports
- **Database Browser**: Connect to PostgreSQL, MySQL, or Snowflake and execute SQL queries
- **Template Rendering**: Render HTML templates with variable substitution and loop support
- **Form & JSON Processing**: Handle URL-encoded forms and JSON payloads
- **Interactive Web UIs**: Built-in script editor, database browser, and module manager
- **RESTful API**: JSON responses for all endpoints with consistent error handling
- **Structured Logging**: Correlation IDs for distributed tracing and request tracking
- **Health Monitoring**: `/health` and `/metrics` endpoints for application monitoring
- **Centralized Build**: Every dependency and plugin version pinned in `shared-build/` — see [docs/build/build-system.md](docs/build/build-system.md)
- **Production Ready**: Packaged as executable JAR with embedded Tomcat server

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL, optional. Expected at `localhost:5432/appdb` with user and
  password `postgres`. Override with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

The app starts without a database, on reduced defaults. See
[docs/architecture/configuration.md](docs/architecture/configuration.md).

## Project Structure

```
servlet-example/
├── pom.xml                     (app: what it needs, no versions)
├── shared-build/               (all versions and build conventions)
│   ├── pom.xml                 (compiler, plugin versions, conventions)
│   └── catalog/
│       ├── thirdparty/pom.xml  (third-party versions)
│       └── internal/pom.xml    (internal/forked artifact versions)
├── data/                       (runtime state, gitignored)
│   ├── attachments/            (chunked file storage, filesystem backend)
│   ├── modules/                (user-created JavaScript modules)
│   └── extlib/                 (JDBC driver jars, downloaded on demand)
├── docs/                       (see docs/readme.md)
│   ├── architecture/  routing/  storage/  scripting/
│   ├── data-browser/  observability/  build/
│   └── development/  troubleshooting/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           ├── Main.java
│       │           ├── servlet/        (RouterServlet entry point)
│       │           ├── route/          (Declarative routing)
│       │           │   ├── RouteRegistry.java
│       │           │   └── RouteDispatcher.java
│       │           ├── handler/        (Singleton handlers)
│       │           │   ├── ApiHandler.java
│       │           │   ├── PropertiesHandler.java
│       │           │   ├── AttachmentHandler.java
│       │           │   ├── DataBrowserHandler.java
│       │           │   └── MongoHandler.java
│       │           ├── processor/      (Request processors)
│       │           │   ├── IRequestProcessor.java
│       │           │   ├── ScriptProcessor.java
│       │           │   ├── ApiScriptProcessor.java
│       │           │   ├── FileUploadProcessor.java
│       │           │   ├── ModuleProcessor.java
│       │           │   └── TemplateProcessor.java
│       │           ├── storage/        (Chunked file storage)
│       │           │   ├── AttachmentManager.java
│       │           │   ├── LocalFileSystemStorage.java
│       │           │   ├── DatabaseAttachmentStorage.java
│       │           │   └── ChunkedOutputStream.java
│       │           ├── config/         (DB config, jOOQ wrapper, Flyway, properties)
│       │           │   ├── DbConfig.java
│       │           │   ├── Database.java         (jOOQ query/transact wrapper)
│       │           │   ├── DbPropertiesLoader.java
│       │           │   ├── PropertyRepository.java
│       │           │   └── AppProperty.java      (Java record)
│       │           ├── datasource/     (Database browser connection strategies)
│       │           ├── script/         (Rhino execution internals)
│       │           ├── security/       (ClassShutter sandbox)
│       │           ├── module/
│       │           │   ├── ModuleManager.java
│       │           │   └── ModuleDependencyResolver.java
│       │           ├── extlib/         (Dynamic JDBC driver loading)
│       │           ├── mongo/          (MongoDB via reflection)
│       │           ├── model/          (Shared domain models)
│       │           ├── exception/
│       │           └── util/
│       │               ├── JsonUtil.java
│       │               ├── PropertiesUtil.java
│       │               ├── StructuredLogger.java
│       │               └── TemplateEngine.java
│       └── resources/
│           ├── application.yml
│           ├── routes.yml              (All 41 routes defined here)
│           ├── db/                     (Flyway migrations)
│           │   ├── V1__create_app_properties.sql
│           │   ├── V2__seed_app_properties.sql
│           │   └── V3__create_attachments.sql
│           ├── META-INF/services/      (DataSourceStrategy SPI registrations)
│           └── static/
│               ├── index.html
│               ├── script-editor.html
│               └── data-browser.html
```

## Configuration

`src/main/resources/application.yml` holds database credentials and nothing else:

```yaml
db:
  url:      ${DB_URL:jdbc:postgresql://localhost:5432/appdb}
  username: ${DB_USERNAME:postgres}
  password: ${DB_PASSWORD:postgres}
```

Every other property lives in the `app_properties` table, seeded by
`db/V2__seed_app_properties.sql`. Lookup order is LRU cache, then the table,
then YAML.

On startup Flyway applies pending migrations and the app switches to DB-backed
property lookup.

Full property table, precedence rules, and no-database behaviour in
[docs/architecture/configuration.md](docs/architecture/configuration.md).

### Environment Variables

```bash
SERVER_PORT=9090 mvn -PappRun
DB_URL=jdbc:postgresql://host:5432/db DB_USERNAME=alice mvn -PappRun
```

Those are the only two mechanisms. Rules and reasons in
[docs/architecture/configuration.md](docs/architecture/configuration.md).

## Building and Running

### Build the executable JAR

```bash
mvn clean package
```

### Run the application

**Production mode (executable JAR):**
```bash
java -jar target/servlet-example.jar
```

**Development mode:**
```bash
mvn -PappRun
```

Confirm it came up:
```bash
curl localhost:8080/health
```

Then open `/` for the dashboard, or `/script-editor`, `/data-browser`,
`/metrics`.

The application will start and display:

```
=========================================
Tomcat server started successfully!
Port: 8080

GET Endpoints:
  - http://localhost:8080/
  - http://localhost:8080/health
  - http://localhost:8080/metrics
  - http://localhost:8080/script-editor (Interactive JavaScript Code Editor with Module Manager)
  - http://localhost:8080/api/modules/list (List all modules)
  - http://localhost:8080/api/modules/{path} (Get specific module)

POST Endpoints:
  - http://localhost:8080/api/upload (Content-Type: multipart/form-data)
  - http://localhost:8080/api/script (Content-Type: application/javascript)
  - http://localhost:8080/api/render (Content-Type: text/html)
  - http://localhost:8080/api/modules/create (Create new module)

PUT Endpoints:
  - http://localhost:8080/api/modules/{path} (Update module)

DELETE Endpoints:
  - http://localhost:8080/api/modules/{path} (Delete module)
=========================================
```

## API Endpoints

### GET Endpoints

#### Root Endpoint
- **URL**: `http://localhost:8080/`
- **Method**: GET
- **Response**: Welcome message with available endpoints

```bash
curl http://localhost:8080/
```

```json
{
  "message": "Welcome to Jakarta EE Servlet Application",
  "version": "1.0",
  "endpoints": {
    "GET": ["/", "/health", "/metrics", "/script-editor"],
    "POST": ["/api/upload", "/api/script", "/api/render"]
  },
  "timestamp": 1234567890
}
```

#### Health Check
- **URL**: `http://localhost:8080/health`
- **Method**: GET
- **Response**: Application health status

```bash
curl http://localhost:8080/health
```

```json
{
  "status": "UP",
  "timestamp": 1234567890,
  "uptime": "123456 ms"
}
```

#### Metrics
- **URL**: `http://localhost:8080/metrics`
- **Method**: GET
- **Response**: System metrics

```bash
curl http://localhost:8080/metrics
```

```json
{
  "metrics": {
    "totalRequests": 42,
    "memory": {
      "used": 12345678,
      "free": 87654321,
      "total": 100000000,
      "max": 200000000
    },
    "threads": {
      "active": 10
    },
    "timestamp": 1234567890
  }
}
```

### POST Endpoints

#### File Upload
Upload files up to 500MB with automatic storage management.

**Upload a file:**
```bash
curl -X POST http://localhost:8080/api/upload \
  -F "file=@/path/to/file.pdf"
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "files": [
      {
        "attachmentId": "5efbb4ae-7910-4410-bc4a-e7590a6674cc",
        "fileName": "file.pdf",
        "size": 5242880,
        "contentType": "application/pdf",
        "hash": "c9999a9c8d5f697...",
        "downloadUrl": "/api/attachment/5efbb4ae-7910-4410-bc4a-e7590a6674cc/download"
      }
    ],
    "fileCount": 1
  }
}
```

**Managing attachments:**
```bash
# List all uploaded files
curl http://localhost:8080/api/attachments

# Download a file
curl -o myfile.pdf http://localhost:8080/api/attachment/{attachmentId}/download

# Get file information
curl http://localhost:8080/api/attachment/{attachmentId}

# Delete a file
curl -X DELETE http://localhost:8080/api/attachment/{attachmentId}
```

Files are automatically split into chunks for efficient storage and streaming. SHA-256 hash is calculated for integrity verification.

#### Properties API

Manage application properties stored in PostgreSQL (requires `db.*` config):

```bash
# List all properties
curl http://localhost:8080/api/properties

# Get a single property
curl http://localhost:8080/api/properties/1

# Create a property
curl -X POST http://localhost:8080/api/properties \
  -H "Content-Type: application/json" \
  -d '{"name":"my.key","value":"hello","type":"string","description":"demo"}'

# Update a property
curl -X PUT http://localhost:8080/api/properties/1 \
  -H "Content-Type: application/json" \
  -d '{"value":"world"}'

# Delete a property
curl -X DELETE http://localhost:8080/api/properties/1
```

Properties are cached in an LRU cache and looked up in order: cache → DB → YAML.

#### JavaScript Execution
- **URL**: `http://localhost:8080/api/script`
- **Method**: POST
- **Content-Type**: `application/javascript`
- **Features**: Server-side JavaScript execution with performance monitoring

```bash
curl -X POST http://localhost:8080/api/script \
  -H "Content-Type: application/javascript" \
  -d '{
    "script": "var sum = 0; for (var i = 1; i <= 100; i++) { sum += i; } console.log(\"Sum:\", sum); sum;",
    "params": {}
  }'
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "result": 5050.0,
    "console": [
      "Sum: 5050"
    ],
    "executionTimeMs": 3,
    "memoryUsedBytes": 1024
  },
  "timestamp": 1234567890
}
```

**Performance Metrics:**
- `executionTimeMs`: Script execution time in milliseconds
- `memoryUsedBytes`: Memory consumed during execution in bytes

**Security Features:**
- Configurable timeout (default: 5000ms)
- Memory limit enforcement (default: 10MB)
- Interpreted mode (optimization level -1) for security
- Instruction observation for resource monitoring

**Available Context:**
- `request.method`: HTTP method
- `request.path`: Request path
- `request.remoteAddr`: Client IP address
- `request.queryParams`: Query parameters
- `console.log()`: Captures output to response

**Java Interop:**
Scripts can create and use Java objects via Rhino with a secure whitelist/blacklist system:

*Allowed Classes (Whitelist):*
- Collections: `ArrayList`, `HashMap`, `HashSet`, `LinkedList`, `TreeMap`, `TreeSet`, etc.
- Utilities: `Date`, `UUID`, `Optional`, `Arrays`, `Collections`
- Primitives/Wrappers: `String`, `StringBuilder`, `Math`, `Integer`, `Long`, `Double`, etc.
- Date/Time: `LocalDate`, `LocalDateTime`, `Instant`, `Duration`, `Period`, etc.
- Math: `BigDecimal`, `BigInteger`

*Blocked for Security (Blacklist):*
- System access: `System`, `Runtime`, `ProcessBuilder`, `Thread`, `ClassLoader`
- File I/O: `java.io.*`, `java.nio.file.*`
- Network: `java.net.*`
- Reflection: `java.lang.reflect.*`
- Database: `java.sql.*`, `javax.sql.*`

```javascript
// Allowed - Collection manipulation
var list = new java.util.ArrayList();
list.add("Hello");
list.add(42);
list.get(0); // Returns "Hello"

var map = new java.util.HashMap();
map.put("name", "Alice");
map.get("name"); // Returns "Alice"

// Blocked - System/File/Network access will fail
java.lang.System.exit(0);           // ERROR: blocked
new java.io.File("/etc/passwd");    // ERROR: blocked
new java.net.Socket("host", 80);    // ERROR: blocked
```

### JavaScript Module System

The application includes a module system that allows you to organize and reuse JavaScript code across scripts.

#### Module Management

Modules are managed through the REST API or the integrated Module Manager UI in the script editor.

**List Modules:**
```bash
curl http://localhost:8080/api/modules/list
```

**Get Module:**
```bash
curl http://localhost:8080/api/modules/utils/string
```

**Create Module:**
```bash
curl -X POST http://localhost:8080/api/modules/create \
  -H "Content-Type: application/json" \
  -d '{
    "path": "utils/math",
    "content": "function add(a, b) { return a + b; }\nfunction multiply(a, b) { return a * b; }\nmodule.exports = { add: add, multiply: multiply };"
  }'
```

**Update Module:**
```bash
curl -X PUT http://localhost:8080/api/modules/utils/math \
  -H "Content-Type: application/json" \
  -d '{
    "content": "function add(a, b) { return a + b; }\nfunction subtract(a, b) { return a - b; }\nmodule.exports = { add: add, subtract: subtract };"
  }'
```

**Delete Module:**
```bash
curl -X DELETE http://localhost:8080/api/modules/utils/math
```

#### Using Modules in Scripts

**Module Structure:**

Modules use CommonJS exports:

```javascript
// File: modules/utils/math.js
function add(a, b) {
    return a + b;
}

function multiply(a, b) {
    return a * b;
}

module.exports = {
    add: add,
    multiply: multiply
};
```

**Importing Modules:**

Scripts use ES6 import syntax (transformed to `require()` internally):

```javascript
// Import the math module
import math from 'utils/math';

// Use module functions
var result = math.add(10, 20);
console.log('Sum:', result);

var product = math.multiply(5, 6);
console.log('Product:', product);

result; // Returns 30
```

**Module Dependencies:**

Modules can import other modules:

```javascript
// File: modules/utils/string.js
module.exports = {
    capitalize: function(str) {
        return str.charAt(0).toUpperCase() + str.slice(1);
    }
};

// File: modules/utils/format.js
import string from 'utils/string';

module.exports = {
    formatName: function(name) {
        return string.capitalize(name.toLowerCase());
    }
};

// In your script:
import format from 'utils/format';
var name = format.formatName('JOHN DOE');
console.log(name); // "John doe"
```

**Key Features:**

- **Namespaced Paths**: Organize modules in directories (e.g., `utils/math`, `helpers/string`)
- **Automatic Dependency Resolution**: Modules are loaded in the correct order
- **Circular Dependency Detection**: Prevents infinite loops with clear error messages
- **ES6 Import Syntax**: Familiar syntax for modern JavaScript developers
- **CommonJS Exports**: Compatible with Node.js module patterns
- **File-based Storage**: Modules stored as `.js` files in the filesystem

**Configuration:**

Module system properties in `app_properties`:

| Property | Default |
|---|---|
| `modules.maxFileSize` | `1048576` (1 MB) |

Change these through the properties API or SQL. There is no environment
variable override for module settings.

#### Interactive Script Editor
- **URL**: `http://localhost:8080/script-editor`
- **Method**: GET
- **Description**: Web-based JavaScript code editor with module management and real-time execution

**Code Editor Tab Features:**
- Live code execution with Ctrl+Enter
- Performance metrics display (execution time and memory usage)
- Console output capture
- Pre-loaded examples (Fibonacci, arrays, Java interop, etc.)
- Dark theme code editor
- ES6 module imports support

**Module Manager Tab Features:**
- Create, edit, and delete JavaScript modules
- Browse available modules in sidebar
- Namespaced module paths (e.g., `utils/string`, `helpers/math`)
- Module information display (size, created/updated timestamps)
- Real-time module list refresh
- CommonJS export syntax support

#### Template Rendering
- **URL**: `http://localhost:8080/api/render`
- **Method**: POST
- **Content-Type**: `text/html`
- **Description**: Custom template engine with variable substitution and loops

```bash
curl -X POST http://localhost:8080/api/render \
  -H "Content-Type: text/html" \
  -d '{
    "template": "<h1>{{title}}</h1><ul>{{#for user in users}}<li>{{user.name}}</li>{{/for}}</ul>",
    "data": {
      "title": "Users",
      "users": [
        {"name": "Alice"},
        {"name": "Bob"}
      ]
    }
  }'
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "html": "<h1>Users</h1><ul><li>Alice</li><li>Bob</li></ul>",
    "size": 48
  },
  "timestamp": 1234567890
}
```

**Template Syntax:**
- Variable substitution: `{{variableName}}`
- Dot notation: `{{user.name}}`
- For loops: `{{#for item in items}}...{{/for}}`
- Automatic XSS protection via HTML escaping

## Architecture

### Request Processing Flow

```
HTTP Request → RouterServlet → RouteRegistry (routes.yml) → RouteDispatcher
                                                                    ↓
                    ┌───────────────────────────────────────────────┴───────────────────────┐
                    ↓                   ↓                   ↓                ↓               ↓
          FileUploadProcessor   ScriptProcessor   TemplateProcessor   ModuleProcessor   Handlers
            (file uploads)        (JavaScript)      (HTML templates)    (modules)    (attachments, DB)
                    ↓                   ↓                   ↓                ↓               ↓
              ProcessorResponse ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ←
                    ↓
         HTTP Response (JSON)
```

### Design Patterns

- **Strategy Pattern**: Request processors implement a common interface
- **Declarative Routing**: YAML-based route configuration with RouteRegistry
- **Builder Pattern**: ProcessorResponse construction
- **Singleton Pattern**: RouteRegistry, AttachmentManager, and PropertiesUtil
- **Template Method**: Placeholder resolution in configuration

### Key Components

1. **RouterServlet**: Minimal servlet (236 lines) handling all HTTP requests
2. **RouteRegistry**: Loads routes.yml and performs pattern matching with validation
3. **RouteDispatcher**: Dispatches requests to handlers/processors via reflection
4. **IRequestProcessor**: Interface for content-type specific processing
5. **ScriptProcessor**: JavaScript execution engine with security sandbox and performance monitoring
6. **ModuleProcessor**: Handles module CRUD operations via REST API
7. **ModuleManager**: Stores and retrieves JavaScript modules in the `modules` table
8. **ModuleDependencyResolver**: Resolves module dependencies and detects circular references
9. **TemplateProcessor**: HTML template rendering with variable substitution
10. **TemplateEngine**: Custom template parser and renderer
11. **AttachmentManager**: Manages chunked file storage (memory-efficient)
12. **CorrelationIdFilter**: Adds correlation IDs to all requests for distributed tracing
13. **StructuredLogger**: Structured logging with automatic MDC context
14. **PropertiesUtil**: YAML configuration loader with environment variable support
15. **JsonUtil**: JSON serialization/deserialization wrapper

## Thread Pool Configuration

The embedded Tomcat server uses these defaults, all `threadPool.*` properties in `app_properties`:

- **Max Threads**: 200 (concurrent requests)
- **Min Spare Threads**: 10
- **Accept Count**: 100 (queue size)
- **Connection Timeout**: 20000 ms

## Technologies

- **Jakarta EE Servlet API** (via Embedded Tomcat 10.1.20)
- **Apache Tomcat Embedded** (web server)
- **Gson 2.10.1** (JSON processing)
- **SnakeYAML 2.2** (YAML configuration)
- **Mozilla Rhino 1.7.15** (JavaScript engine for server-side execution)
- **jOOQ 3.19.11** (type-safe SQL DSL for PostgreSQL)
- **Flyway 9.22.3** (database schema migrations)
- **PostgreSQL JDBC 42.7.9** (database driver)
- **Lombok 1.18.30** (boilerplate reduction)
- **Java 17**
- **Maven** (build tool with Shade plugin for executable JAR)

## Error Handling

The application returns appropriate HTTP status codes:

- `200 OK` - Successful processing
- `400 Bad Request` - Malformed data (invalid JSON, empty body, script errors, etc.)
- `404 Not Found` - Unknown endpoint
- `408 Request Timeout` - Script execution timeout exceeded
- `413 Payload Too Large` - File size or memory limit exceeded
- `415 Unsupported Media Type` - No processor for Content-Type
- `500 Internal Server Error` - Processing exception

Error responses follow this format:

```json
{
  "error": "Error type",
  "message": "Detailed error message",
  "status": 400,
  "timestamp": 1234567890
}
```

## Development

### Adding a New Processor

1. Implement the `RequestProcessor` interface:
```java
public class XmlDataProcessor implements RequestProcessor {
    @Override
    public boolean supports(String contentType) {
        return contentType != null && contentType.startsWith("application/xml");
    }

    @Override
    public ProcessorResponse process(HttpServletRequest request)
            throws IOException, ServletException {
        // Process XML data
        return ProcessorResponse.builder()
                .statusCode(200)
                .body(responseJson)
                .build();
    }

    @Override
    public String getContentType() {
        return "application/xml";
    }
}
```

2. Register in `RouterServlet.init()`:
```java
registry.register(new XmlDataProcessor());
```

3. Add route in `RouterServlet.doPost()`:
```java
case "/api/xml":
    handleProcessorRequest(request, response);
    break;
```

## License

This project is open source and available under the MIT License.
