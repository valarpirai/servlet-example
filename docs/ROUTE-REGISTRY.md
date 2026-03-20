# Route Registry System

## Overview

The route registry system externalizes route configuration from Java code to YAML files, making it easier to maintain and modify routes without recompiling.

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
- `static` - Serve static files from classpath
- `handler` - Invoke custom handler methods
- `processor` - Content-type based processors
- `builtin` - Built-in RouterServlet methods

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

### After (Route Registry)
```java
// RouterServlet.java
@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    RouteRegistry registry = RouteRegistry.getInstance();
    RouteDispatcher dispatcher = new RouteDispatcher();

    RouteRegistry.RouteMatch match = registry.findRoute("GET", request.getPathInfo());

    if (match != null) {
        dispatcher.dispatch(match, request, response);
    } else {
        handleNotFound(response, out, request.getPathInfo());
    }
}
```

**Benefits:**
- ✅ Routes defined in YAML (no Java changes)
- ✅ Clean, simple dispatch logic
- ✅ Automatic path parameter extraction
- ✅ Easy to add/modify routes
- ✅ All routes visible in one place
- ✅ Type-safe pattern matching

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

## Testing

Run route registry tests:
```bash
mvn test -Dtest=RouteRegistryTest
```

**Test Coverage:**
- ✅ Route loading from YAML
- ✅ Exact path matching
- ✅ Path parameter extraction
- ✅ Wildcard matching
- ✅ Method filtering
- ✅ Static file routes
- ✅ All endpoint types

## Integration with RouterServlet

The route registry is **optional** and can be integrated gradually:

1. **Current approach**: Keep existing RouterServlet logic
2. **Hybrid approach**: Use registry for new routes, keep old code for existing routes
3. **Full migration**: Replace all if-else chains with dispatcher

**Recommended: Hybrid Approach**
```java
@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    // Try route registry first
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
