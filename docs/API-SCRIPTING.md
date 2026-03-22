# REST API Scripting Feature

## Overview

The servlet application now supports scripted REST API endpoints using JavaScript. This allows you to create dynamic API handlers without recompiling Java code.

## Architecture

- **ApiScriptProcessor**: Executes JavaScript files with Rhino engine
- **ApiHandler**: Routes HTTP requests to appropriate JS scripts
- **ScriptSecurityManager**: Shared security (ClassShutter, timeout, memory limits)
- **Route**: `/api/v1/{endpoint-name}` → `scripts/api/{endpoint-name}.js`

## Features

✅ **Full HTTP Support**: GET, POST, PUT, DELETE, PATCH
✅ **Request/Response Objects**: Access to method, path, query params, headers, body
✅ **Module System**: Use `require()` to load shared libraries from `scripts/lib/` and `scripts/vendor/`
✅ **Security**: Same sandbox as ScriptProcessor (ClassShutter, timeout, memory limits)
✅ **Error Handling**: Stack traces in dev mode (`ENVIRONMENT=dev`)
✅ **JSON Support**: Automatic JSON parsing for request bodies

## Quick Start

### 1. Create an API Handler

**File**: `scripts/api/users.js`

```javascript
function httpHandler(request, response) {
  if (request.method === 'GET') {
    response.setStatus(200);
    response.setBody(JSON.stringify({
      message: 'Hello from scripted API!',
      users: ['Alice', 'Bob', 'Charlie']
    }));
  } else {
    response.setStatus(405);
    response.setBody(JSON.stringify({
      error: 'Method not allowed'
    }));
  }
}
```

### 2. Test the Endpoint

```bash
curl http://localhost:8080/api/v1/users
```

Response:
```json
{
  "message": "Hello from scripted API!",
  "users": ["Alice", "Bob", "Charlie"]
}
```

## API Reference

### Request Object

```javascript
{
  method: "GET",              // HTTP method
  path: "/api/v1/users",      // Request path
  queryParams: {              // Query string parameters
    page: "1",
    limit: "10"
  },
  headers: {                  // HTTP headers
    "Content-Type": "application/json",
    "Authorization": "Bearer ..."
  },
  body: {                     // Parsed JSON body (POST/PUT/PATCH)
    name: "Alice",
    email: "alice@example.com"
  }
}
```

### Response Object

```javascript
// Set status code
response.setStatus(200);      // 200, 201, 400, 404, 500, etc.

// Set custom headers
response.setHeader("X-Custom-Header", "value");
response.setHeader("Content-Type", "application/xml");

// Set response body (string or object)
response.setBody("plain text");
response.setBody(JSON.stringify({ key: "value" }));
response.setBody({ key: "value" });  // Auto-converts to JSON
```

## Module System

### Using Shared Libraries

**File**: `scripts/lib/validators.js`

```javascript
function validateEmail(email) {
  return email.includes('@');
}

function validateRequired(obj, fields) {
  var missing = [];
  for (var i = 0; i < fields.length; i++) {
    if (!obj[fields[i]]) {
      missing.push(fields[i]);
    }
  }
  return missing;
}

module.exports = {
  validateEmail: validateEmail,
  validateRequired: validateRequired
};
```

**File**: `scripts/api/register.js`

```javascript
var validators = require('../lib/validators.js');

function httpHandler(request, response) {
  if (request.method !== 'POST') {
    response.setStatus(405);
    response.setBody({ error: 'Method not allowed' });
    return;
  }

  var missing = validators.validateRequired(request.body, ['email', 'password']);
  if (missing.length > 0) {
    response.setStatus(400);
    response.setBody({
      error: 'Missing required fields',
      fields: missing
    });
    return;
  }

  if (!validators.validateEmail(request.body.email)) {
    response.setStatus(400);
    response.setBody({ error: 'Invalid email format' });
    return;
  }

  // Process registration...
  response.setStatus(201);
  response.setBody({ message: 'User registered successfully' });
}
```

## Security

### Same as ScriptProcessor

- **Timeout**: 5 seconds (configurable via `script.timeout`)
- **Memory Limit**: 50 MB (configurable via `script.maxMemory`)
- **ClassShutter**: Blocks access to file system, network, reflection, etc.

### Configuration

```yaml
# application.yml
script:
  timeout: 5000                 # 5 seconds
  maxMemory: 52428800           # 50 MB
  optimizationLevel: -1         # Interpreted mode
  instructionThreshold: 10000   # Instruction count for checks
```

### Environment Variable

```bash
# Enable stack traces in error responses
ENVIRONMENT=dev mvn -PappRun

# Disable stack traces (production)
ENVIRONMENT=prod mvn -PappRun
```

## Examples

See the following example scripts:

- **`scripts/api/hello.js`**: Simple hello world endpoint
- **`scripts/api/users.js`**: Full CRUD example with in-memory storage
- **`scripts/lib/utils.js`**: Shared utility functions

## Testing

Tests are located in:
- **`ApiScriptProcessorTest.java`**: Unit tests for script execution
- **`ApiHandlerTest.java`**: Integration tests for HTTP handling

Run tests:
```bash
mvn test -Dtest=ApiScriptProcessorTest,ApiHandlerTest
```

## Current Status

✅ **Core Functionality**: Working
✅ **Security Integration**: Complete
✅ **Route Registration**: Complete
✅ **Example Scripts**: Available
⚠️ **Tests**: 11/17 passing (some failures with request object conversion)

## Known Issues

1. **Request Object Conversion**: Some tests fail when accessing nested request properties (query params, body) in JavaScript. The conversion from Java Maps to JavaScript objects needs refinement.

2. **Module Caching**: Scripts are cached in production mode but always reloaded in dev mode.

## Future Enhancements

- [ ] Async/await support (requires different JS engine)
- [ ] Built-in database access helpers
- [ ] Session management utilities
- [ ] File upload handling in scripts
- [ ] WebSocket support

## References

- **Implementation**: `ApiScriptProcessor.java`, `ApiHandler.java`
- **Security**: `ScriptSecurityManager.java`
- **Configuration**: `application.yml`, `routes.yml`
- **Tests**: `src/test/java/com/example/servlet/processor/`
