# Jakarta EE Servlet Example

A modern web application using Jakarta EE Servlets with **declarative YAML routing**, packaged as an executable JAR with embedded Apache Tomcat.

## Features

- **Beautiful Home Page**: Visit `/` for an elegant HTML dashboard with links to all endpoints
- **Declarative Routing**: All 21 routes configured in `routes.yml` - no hardcoded routing logic
- **File Upload & Storage**: Upload files up to 500MB with automatic chunked storage (1MB chunks), SHA-256 hashing, and streaming downloads
- **Server-Side JavaScript**: Execute JavaScript code using Rhino engine with console output and performance metrics
- **JavaScript Modules**: Create reusable modules with ES6 imports and CommonJS exports
- **Database Browser**: Connect to PostgreSQL, MySQL, or Snowflake and execute SQL queries
- **Template Rendering**: Render HTML templates with variable substitution and loop support
- **Form & JSON Processing**: Handle URL-encoded forms and JSON payloads
- **Interactive Web UIs**: Built-in script editor, database browser, and module manager
- **RESTful API**: JSON responses for all endpoints with consistent error handling
- **Health Monitoring**: `/health` and `/metrics` endpoints for application monitoring
- **YAML Configuration**: Easy configuration via `application.yml` with environment variable overrides
- **Production Ready**: Packaged as executable JAR with embedded Tomcat server (236-line servlet)

## Prerequisites

- Java 17 or higher
- Maven 3.6+

## Project Structure

```
servlet-example/
├── pom.xml
├── modules/                 (JavaScript modules storage)
├── attachments/             (Chunked file storage)
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           ├── Main.java
│       │           ├── datasource/      (Database connection strategies)
│       │           ├── extlib/          (Dynamic JDBC driver loading)
│       │           └── servlet/
│       │               ├── RouterServlet.java (236 lines - minimal)
│       │               ├── route/       (Declarative routing)
│       │               │   ├── RouteRegistry.java
│       │               │   ├── RouteDispatcher.java
│       │               │   └── Route.java (Lombok model)
│       │               ├── handler/     (Singleton handlers)
│       │               │   ├── AttachmentHandler.java
│       │               │   └── DataBrowserHandler.java
│       │               ├── processor/   (Request processors)
│       │               │   ├── RequestProcessor.java (interface)
│       │               │   ├── ProcessorResponse.java (Lombok builder)
│       │               │   ├── FileUploadProcessor.java
│       │               │   ├── FormDataProcessor.java
│       │               │   ├── JsonDataProcessor.java
│       │               │   ├── ModuleProcessor.java
│       │               │   ├── ScriptProcessor.java
│       │               │   └── TemplateProcessor.java
│       │               ├── storage/     (Chunked file storage)
│       │               │   ├── AttachmentManager.java
│       │               │   ├── LocalFileSystemStorage.java
│       │               │   └── ChunkedOutputStream.java
│       │               ├── module/
│       │               │   ├── ModuleManager.java
│       │               │   └── ModuleDependencyResolver.java
│       │               ├── model/       (Lombok models)
│       │               │   ├── Attachment.java
│       │               │   ├── Module.java
│       │               │   ├── Route.java
│       │               │   └── ProcessorResponse.java
│       │               └── util/
│       │                   ├── JsonUtil.java
│       │                   ├── PropertiesUtil.java
│       │                   └── TemplateEngine.java
│       └── resources/
│           ├── application.yml
│           ├── routes.yml              (All 21 routes defined here)
│           └── static/
│               ├── index.html          (Beautiful home page)
│               ├── script-editor.html
│               └── data-browser.html
```

## Configuration

Configuration is managed through `src/main/resources/application.yml`:

```yaml
# Server Configuration
server:
  port: ${SERVER_PORT:8080}  # Supports environment variable override

# File Upload Configuration
upload:
  maxFileSize: 10485760        # 10 MB
  maxRequestSize: 52428800     # 50 MB
  fileSizeThreshold: 1048576   # 1 MB
  tempDirectory: ${java.io.tmpdir}

# Thread Pool Configuration
threadPool:
  maxThreads: 200
  minSpareThreads: 10
  acceptCount: 100
  connectionTimeout: 20000

# Module System Configuration
modules:
  directory: modules       # Module storage directory
  maxFileSize: 1048576    # Max module file size (1 MB)
```

### Environment Variables

Override configuration using environment variables:

```bash
# Change server port
SERVER_PORT=9090 java -jar target/servlet-example.jar

# Change modules directory
MODULES_DIR=/custom/modules/path java -jar target/servlet-example.jar

# Multiple overrides
SERVER_PORT=9090 MODULES_DIR=./my-modules mvn -PappRun
```

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

**Development mode (similar to bootRun):**
```bash
mvn -PappRun
```

Or alternatively:
```bash
mvn compile exec:java
```

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
  - http://localhost:8080/api/form   (Content-Type: application/x-www-form-urlencoded)
  - http://localhost:8080/api/json   (Content-Type: application/json)
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
    "POST": ["/api/form", "/api/json", "/api/upload", "/api/script", "/api/render"]
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

#### Form Data Submission
- **URL**: `http://localhost:8080/api/form`
- **Method**: POST
- **Content-Type**: `application/x-www-form-urlencoded`

```bash
curl -X POST http://localhost:8080/api/form \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "name=John&email=john@example.com&age=30"
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "name": "John",
    "email": "john@example.com",
    "age": "30"
  },
  "timestamp": 1234567890
}
```

#### JSON Data Processing
- **URL**: `http://localhost:8080/api/json`
- **Method**: POST
- **Content-Type**: `application/json`

```bash
curl -X POST http://localhost:8080/api/json \
  -H "Content-Type: application/json" \
  -d '{"name":"Jane","email":"jane@example.com","preferences":{"theme":"dark"}}'
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "received": {
      "name": "Jane",
      "email": "jane@example.com",
      "preferences": {
        "theme": "dark"
      }
    },
    "size": 75
  },
  "timestamp": 1234567890
}
```

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

Module system settings in `application.yml`:

```yaml
modules:
  directory: modules        # Module storage directory
  maxFileSize: 1048576     # Max file size (1 MB)
```

Environment variable override:
```bash
MODULES_DIR=/custom/path java -jar target/servlet-example.jar
```

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
HTTP Request → RouterServlet → Content-Type Check → ProcessorRegistry
                                                            ↓
                    ┌───────────────────────────────────────┴───────────────────────────────┐
                    ↓                   ↓                   ↓                ↓               ↓
          FormDataProcessor   JsonDataProcessor   FileUploadProcessor   ScriptProcessor   TemplateProcessor
         (urlencoded forms)   (JSON payloads)     (file uploads)        (JavaScript)      (HTML templates)
                    ↓                   ↓                   ↓                ↓               ↓
              ProcessorResponse ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ←
                    ↓
         HTTP Response (JSON)
```

### Design Patterns

- **Strategy Pattern**: Request processors implement a common interface
- **Registry Pattern**: Central registry for processor discovery and routing
- **Builder Pattern**: ProcessorResponse construction
- **Singleton Pattern**: ProcessorRegistry and PropertiesUtil
- **Template Method**: Placeholder resolution in configuration

### Key Components

1. **RouterServlet**: Central servlet handling all HTTP requests
2. **ProcessorRegistry**: Manages and routes to appropriate processors
3. **RequestProcessor**: Interface for content-type specific processing
4. **ScriptProcessor**: JavaScript execution engine with performance monitoring and module support
5. **ModuleProcessor**: Handles module CRUD operations via REST API
6. **ModuleManager**: Manages filesystem storage and retrieval of JavaScript modules
7. **ModuleDependencyResolver**: Resolves module dependencies and detects circular references
8. **TemplateProcessor**: HTML template rendering with variable substitution
9. **TemplateEngine**: Custom template parser and renderer
10. **PropertiesUtil**: YAML configuration loader with environment variable support
11. **JsonUtil**: JSON serialization/deserialization wrapper

## Thread Pool Configuration

The embedded Tomcat server uses the following defaults (configurable in application.yml):

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
