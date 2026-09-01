# Adding a Route

Four steps for a new handler route. Fewer if the handler already exists.

## 1. Add the route to routes.yml

Place it above any wildcard that would swallow it.

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

## 2. Write the handler

Handlers are singletons. The method signature takes the response, then each path
parameter in the order listed under `pathParams`.

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
    // write the response
  }
}
```

## 3. Register the name in both places

This is the step people miss. See
[route registry](./route-registry.md) for why there are two.

In `RouteRegistry.getHandlerClassName`, for startup validation:

```java
case "UserHandler":
  return "com.example.handler.UserHandler";
```

In `RouteDispatcher.getHandlerInstance`, for dispatch:

```java
case "UserHandler":
  return UserHandler.getInstance();
```

## 4. Test it

```bash
mvn test -Dtest=RouteRegistryTest,RouteDispatcherTest
```

No servlet changes are needed. `RouterServlet` never names a route.

## Order matters

Routes match in file order, and the first match wins. Specific paths must come
before wildcards.

```yaml
# Correct
- path: /api/attachment/{id}/download
- path: /api/attachment/{id}
- path: /api/**

# Wrong: the wildcard matches everything, the others never run
- path: /api/**
- path: /api/attachment/{id}/download
```

## Choosing a type

Pick `handler` when the endpoint streams, or when it needs the response object.
Pick `processor` when it returns a value and holds per-request state. A processor
cannot stream, because it returns before anything is written.

## Adding a processor instead

Same shape, two differences. Register in `getProcessorClassName` and
`getProcessorInstance`, and implement `IRequestProcessor` — startup validation
checks for that interface.

## Related

- [Route configuration](./route-configuration.md) — every field
- [Route registry](./route-registry.md) — validation and dispatch
- [Routing issues](../troubleshooting/routing-issues.md) — when it still 404s
