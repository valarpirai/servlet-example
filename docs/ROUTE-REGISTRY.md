# Route Registry System

## Overview

The route registry system is the **core routing mechanism** for the application. All 21 routes are defined in `routes.yml`, eliminating hardcoded routing logic and reducing RouterServlet from 461 to 236 lines (49% reduction).

## Architecture

```
routes.yml (config)
    ↓
RouteRegistry (loads & matches routes)
    ↓
RouteDispatcher (dispatches to handlers)
    ↓
Handlers/Processors (execute business logic)
```

## Components

### 1. `routes.yml`
Central configuration file defining all application routes.

**Route Definition:**
```yaml
- path: /api/attachment/{id}/download
  method: GET
  type: handler
  handler: AttachmentHandler
  handlerMethod: handleDownload
  pathParams:
    - id
  description: Download attachment by ID (streaming)
```

**Supported Route Types:**

1. **`static`** - Serve static HTML/CSS/JS files from classpath
```yaml
- path: /
  type: static
  resource: static/index.html
  contentType: text/html
```

2. **`handler`** - Invoke singleton handler methods via reflection
```yaml
- path: /api/attachment/{id}/download
  type: handler
  handler: AttachmentHandler
  handlerMethod: handleDownload
  pathParams: [id]
```

3. **`processor`** - Instantiate RequestProcessor implementations
```yaml
- path: /api/json
  type: processor
  processor: JsonDataProcessor  # Instantiated by RouteDispatcher
  contentType: application/json
```

4. **`builtin`** - Built-in RouterServlet methods (/health, /metrics)
```yaml
- path: /health
  type: builtin
  handler: handleHealth
```

### 2. `RouteRegistry.java`
Singleton that loads routes.yml and provides route matching.

**Key Features:**
- Loads routes on startup
- Pattern matching with regex
- Path parameter extraction: `{id}` → captured value
- Wildcard support: `/api/modules/**`

**Usage:**
```java
RouteRegistry registry = RouteRegistry.getInstance();
RouteRegistry.RouteMatch match = registry.findRoute("GET", "/api/attachment/abc-123/download");

if (match != null) {
    Route route = match.getRoute();
    String id = match.getPathParam("id"); // "abc-123"
}
```

### 3. `Route.java`
Represents a single route with:
- Path pattern (with parameter placeholders)
- HTTP methods (GET, POST, etc.)
- Handler type and method name
- Path parameters
- Compiled regex pattern

### 4. `RouteDispatcher.java`
Dispatches requests to appropriate handlers using reflection.

**Dispatcher Flow:**
```
Request → RouteRegistry.findRoute()
        → RouteDispatcher.dispatch()
        → Handler/Processor invocation
        → Response
```

## Benefits

### Before (Hardcoded Routes)
```java
// RouterServlet.java
@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    String path = request.getPathInfo();

    if ("/script-editor".equals(path)) {
        serveStaticFile(request, response, "static/script-editor.html", "text/html");
        return;
    }

    if ("/data-browser".equals(path)) {
        serveStaticFile(request, response, "static/data-browser.html", "text/html");
        return;
    }

    if (path != null && path.startsWith("/api/modules")) {
        handleModulesRequest(request, response);
        return;
    }

    if (path != null && path.equals("/api/attachments")) {
        AttachmentHandler.getInstance().handleList(response);
        return;
    }

    if (path != null && path.startsWith("/api/attachment/")) {
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            String attachmentId = parts[3];
            String action = parts.length > 4 ? parts[4] : "metadata";

            if ("download".equals(action)) {
                AttachmentHandler.getInstance().handleDownload(request, response, attachmentId);
            } else {
                AttachmentHandler.getInstance().handleMetadata(response, attachmentId);
            }
        }
        return;
    }

    // ... 50 more lines of if-else chains
}
```

**Problems:**
- ❌ Hardcoded routes in Java code
- ❌ Complex if-else chains
- ❌ Manual path parsing with string manipulation
- ❌ Requires recompilation for route changes
- ❌ Difficult to visualize all routes
- ❌ Error-prone string operations

### After (Route Registry) - Current Implementation
```java
// RouterServlet.java - Now only 236 lines (was 461)
@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    handleRequest(request, response, () -> dispatchOrNotFound(request, response));
}

private void dispatchOrNotFound(HttpServletRequest request, HttpServletResponse response) {
    String path = request.getPathInfo();
    RouteRegistry.RouteMatch match = RouteRegistry.getInstance().findRoute(method, path);

    if (match != null && routeDispatcher.dispatch(match, request, response)) {
        return; // Handled successfully
    }

    // Handle builtin routes (/health, /metrics) or return 404
    if (match != null && "builtin".equals(match.getRoute().getType())) {
        // ... builtin handler logic
    } else {
        // Return 404 JSON
    }
}
}
```

**Benefits Achieved:**
- ✅ **49% code reduction**: RouterServlet reduced from 461 to 236 lines
- ✅ **Single source of truth**: All 21 routes defined in routes.yml
- ✅ **No ProcessorRegistry**: Processors instantiated declaratively by RouteDispatcher
- ✅ **No hardcoded routing**: Zero if-else chains for route matching
- ✅ **Automatic path parameters**: `{id}` extraction without manual string parsing
- ✅ **Wildcard support**: `/api/modules/**` matches all sub-paths
- ✅ **Easy route additions**: Edit YAML, add processor case, done
- ✅ **Comprehensive tests**: 11 RouteDispatcher tests + 10 RouteRegistry tests
- ✅ **4 route types**: static, handler, processor, builtin

## Route Configuration Examples

### Static File
```yaml
- path: /script-editor
  method: GET
  type: static
  resource: static/script-editor.html
  contentType: text/html
  description: Interactive JavaScript code editor
```

### Handler with Path Parameters
```yaml
- path: /api/attachment/{id}/download
  method: GET
  type: handler
  handler: AttachmentHandler
  handlerMethod: handleDownload
  pathParams:
    - id
  description: Download attachment by ID
```

### Wildcard Route
```yaml
- path: /api/modules/**
  method: [GET, POST, PUT, DELETE]
  type: processor
  processor: ModuleProcessor
  contentType: application/json
  description: Module management endpoints
```

### Built-in Handler
```yaml
- path: /health
  method: GET
  type: builtin
  handler: handleHealth
  contentType: application/json
  description: Health check endpoint
```

### Processor Route (No ProcessorRegistry Needed)
```yaml
- path: /api/json
  method: POST
  type: processor
  processor: JsonDataProcessor  # RouteDispatcher instantiates this
  contentType: application/json
  description: JSON data processing
```

**RouteDispatcher.getProcessorInstance():**
```java
switch (processorName) {
    case "JsonDataProcessor": return new JsonDataProcessor();
    case "FormDataProcessor": return new FormDataProcessor();
    case "ScriptProcessor": return new ScriptProcessor();
    // ... add new processors here
}
```

## Testing

Run route registry tests:
```bash
mvn test -Dtest=RouteRegistryTest      # 10 tests - pattern matching
mvn test -Dtest=RouteDispatcherTest    # 11 tests - response handling
mvn test                               # 72 total tests (all passing)
```

**RouteRegistryTest Coverage:**
- ✅ Route loading from YAML (21 routes)
- ✅ Exact path matching
- ✅ Path parameter extraction (`{id}`)
- ✅ Wildcard matching (`/**`)
- ✅ Method filtering (GET, POST, PUT, DELETE)
- ✅ All route types (static, handler, processor, builtin)

**RouteDispatcherTest Coverage:**
- ✅ Static file serving (HTML with correct content-type)
- ✅ Static file 404 handling
- ✅ Handler routes (with/without path params)
- ✅ Processor routes (all 5 processors)
- ✅ Builtin routes (return false by design)
- ✅ Proper response types (HTML, JSON, streams)

## Integration Status

**✅ FULLY INTEGRATED** - The route registry is now the core routing mechanism.

**Current Architecture:**
```java
// RouterServlet.java (236 lines)
@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    handleRequest(request, response, () -> dispatchOrNotFound(request, response));
}
    RouteRegistry registry = RouteRegistry.getInstance();
    RouteRegistry.RouteMatch match = registry.findRoute("GET", request.getPathInfo());

    if (match != null && !"builtin".equals(match.getRoute().getType())) {
        RouteDispatcher dispatcher = new RouteDispatcher();
        if (dispatcher.dispatch(match, request, response)) {
            return; // Successfully handled
        }
    }

    // Fallback to existing if-else logic for builtin handlers
    String path = request.getPathInfo();
    // ... existing code
}
```

## Adding New Routes

To add a new route:

1. **Edit routes.yml**:
```yaml
- path: /api/users/{userId}/profile
  method: GET
  type: handler
  handler: UserHandler
  handlerMethod: getProfile
  pathParams:
    - userId
  contentType: application/json
  description: Get user profile
```

2. **Create Handler** (if needed):
```java
public class UserHandler {
    private static UserHandler instance;

    public static synchronized UserHandler getInstance() {
        if (instance == null) {
            instance = new UserHandler();
        }
        return instance;
    }

    public void getProfile(HttpServletResponse response, String userId) throws IOException {
        // Implementation
    }
}
```

3. **Register in RouteDispatcher**:
```java
private Object getHandlerInstance(String handlerName) {
    switch (handlerName) {
        case "AttachmentHandler":
            return AttachmentHandler.getInstance();
        case "UserHandler":
            return UserHandler.getInstance(); // Add here
        default:
            return null;
    }
}
```

4. **No servlet changes required!**

## Route Matching Order

Routes are matched in the order they appear in routes.yml. More specific routes should come before wildcard routes:

```yaml
# ✅ Correct order
- path: /api/attachment/{id}/download    # Specific
- path: /api/attachment/{id}              # Less specific
- path: /api/**                           # Wildcard (catch-all)

# ❌ Incorrect order
- path: /api/**                           # Would match everything first!
- path: /api/attachment/{id}/download    # Never reached
```

## Performance

- Route loading: O(n) on startup (happens once)
- Route matching: O(n) per request (linear scan)
- Pattern compilation: Cached after loading

For high-performance applications with many routes, consider adding an index or trie structure.

## Future Enhancements

- [ ] Route groups with common prefix
- [ ] Middleware/interceptors per route
- [ ] Route validation on startup
- [ ] Hot-reload routes without restart
- [ ] OpenAPI/Swagger generation from routes.yml
- [ ] Route-specific CORS configuration
- [ ] Rate limiting per route
- [ ] Route metrics and monitoring

## See Also

- `routes.yml` - Route configuration file
- `RouteRegistryTest.java` - Test examples
- `CLAUDE.md` - Technical implementation details
