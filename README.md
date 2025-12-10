# Jakarta EE Servlet Example

A simple web application using Jakarta EE Servlets with content-type based request processing, packaged as an executable JAR with embedded Apache Tomcat.

## Features

- **Content-Type Request Processors**: Automatic routing based on request Content-Type header
  - File Upload Processor (multipart/form-data)
  - Form Data Processor (application/x-www-form-urlencoded)
  - JSON Data Processor (application/json)
  - JavaScript Execution Engine (application/javascript) - Server-side JS with Rhino
  - Template Rendering Engine (text/html) - Custom template engine with variable substitution
- **Interactive Script Editor**: Web-based JavaScript code editor with real-time execution
- **Performance Monitoring**: Execution time and memory usage tracking for scripts
- **YAML Configuration**: Externalized configuration with environment variable support
- **Health & Metrics**: Built-in monitoring endpoints
- **Strategy Pattern**: Clean, extensible architecture for request processing
- **Pure Jakarta EE**: No frameworks, just servlets (with Gson for JSON and SnakeYAML for config)
- **Embedded Tomcat**: Self-contained executable JAR

## Prerequisites

- Java 17 or higher
- Maven 3.6+

## Project Structure

```
servlet-example/
├── pom.xml
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           ├── Main.java
│       │           └── servlet/
│       │               ├── RouterServlet.java
│       │               ├── processor/
│       │               │   ├── RequestProcessor.java (interface)
│       │               │   ├── ProcessorRegistry.java
│       │               │   ├── ProcessorResponse.java
│       │               │   ├── FileUploadProcessor.java
│       │               │   ├── FormDataProcessor.java
│       │               │   ├── JsonDataProcessor.java
│       │               │   ├── ScriptProcessor.java
│       │               │   └── TemplateProcessor.java
│       │               └── util/
│       │                   ├── JsonUtil.java
│       │                   ├── PropertiesUtil.java
│       │                   └── TemplateEngine.java
│       └── resources/
│           ├── application.yml
│           └── static/
│               └── script-editor.html
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
```

### Environment Variables

Override configuration using environment variables:

```bash
# Change server port
SERVER_PORT=9090 java -jar target/servlet-example.jar

# Or for development
SERVER_PORT=9090 mvn -PappRun
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
  - http://localhost:8080/script-editor (Interactive JavaScript Code Editor)

POST Endpoints:
  - http://localhost:8080/api/form   (Content-Type: application/x-www-form-urlencoded)
  - http://localhost:8080/api/json   (Content-Type: application/json)
  - http://localhost:8080/api/upload (Content-Type: multipart/form-data)
  - http://localhost:8080/api/script (Content-Type: application/javascript)
  - http://localhost:8080/api/render (Content-Type: text/html)
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
- **URL**: `http://localhost:8080/api/upload`
- **Method**: POST
- **Content-Type**: `multipart/form-data`
- **Max File Size**: 10 MB (configurable)

```bash
curl -X POST http://localhost:8080/api/upload \
  -F "file=@/path/to/file.txt" \
  -F "description=Test upload"
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "files": [
      {
        "fieldName": "file",
        "fileName": "file.txt",
        "size": 1234,
        "contentType": "text/plain",
        "savedPath": "/tmp/upload-123456-file.txt"
      }
    ],
    "fields": {
      "description": "Test upload"
    },
    "fileCount": 1
  },
  "timestamp": 1234567890
}
```

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
Scripts can create and use Java objects via Rhino:
```javascript
var ArrayList = java.util.ArrayList;
var list = new ArrayList();
list.add("item");
list.size(); // Returns 1
```

#### Interactive Script Editor
- **URL**: `http://localhost:8080/script-editor`
- **Method**: GET
- **Description**: Web-based JavaScript code editor with syntax highlighting and real-time execution

Features:
- Live code execution with Ctrl+Enter
- Performance metrics display (execution time and memory usage)
- Console output capture
- Pre-loaded examples (Fibonacci, arrays, Java interop, etc.)
- Dark theme code editor

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
4. **ScriptProcessor**: JavaScript execution engine with performance monitoring
5. **TemplateProcessor**: HTML template rendering with variable substitution
6. **TemplateEngine**: Custom template parser and renderer
7. **PropertiesUtil**: YAML configuration loader with environment variable support
8. **JsonUtil**: JSON serialization/deserialization wrapper

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
