# Jakarta EE Servlet Example

A simple web application using Plain HTTP Servlet with Jakarta EE, packaged as an executable JAR with embedded Apache Tomcat.

## Features

- Single RouterServlet handling all routes
- Health check endpoint
- Metrics endpoint with memory and request statistics
- Pure Jakarta EE Servlet implementation (no frameworks)
- Embedded Tomcat server
- Executable JAR packaging

## Prerequisites

- Java 17 or higher
- Maven 3.6+

## Project Structure

```
servlet-example/
├── pom.xml
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── example/
│                   ├── Main.java
│                   └── servlet/
│                       └── RouterServlet.java
```

## Building and Running

### Build the executable JAR

```bash
mvn clean package
```

### Run the application

```bash
java -jar target/servlet-example.jar
```

The application will start on port 8080 and display startup information:

```
=========================================
Tomcat server started successfully!
Port: 8080
Endpoints:
  - http://localhost:8080/
  - http://localhost:8080/health
  - http://localhost:8080/metrics
=========================================
```

## Endpoints

### Root Endpoint
- **URL**: `http://localhost:8080/`
- **Method**: GET
- **Response**: JSON with welcome message and available endpoints
```json
{
  "message": "Welcome to Jakarta EE Servlet Application",
  "version": "1.0",
  "endpoints": ["/health", "/metrics"],
  "timestamp": 1234567890
}
```

### Health Endpoint
- **URL**: `http://localhost:8080/health`
- **Method**: GET
- **Response**: JSON with application health status
```json
{
  "status": "UP",
  "timestamp": 1234567890,
  "uptime": "123456 ms"
}
```

### Metrics Endpoint
- **URL**: `http://localhost:8080/metrics`
- **Method**: GET
- **Response**: JSON with application metrics
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

## Architecture

The application uses a single `RouterServlet` that handles all incoming requests and routes them to the appropriate handler method based on the request path. This centralized routing approach simplifies the servlet configuration and makes it easier to manage all endpoints in one place.

The embedded Tomcat server is started from the `Main` class, which programmatically configures and registers the servlet.

## Technologies

- Jakarta EE Servlet (via Embedded Tomcat 10.1)
- Apache Tomcat Embedded
- Java 17
- Maven with Shade Plugin (for executable JAR)
