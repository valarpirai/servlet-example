# Adding a Route

Four steps for a route to a new Java handler. Fewer if the handler already exists.

A route to a JavaScript handler needs no Java. Insert a `script` route. See
[table routes](./table-routes.md).

## 1. Add the route to the routes table

A route that ships with code goes in a Flyway migration, as a default route.
Give it a `sort_order` above any wildcard that would swallow it.

```sql
-- db/V9__add_user_profile_route.sql
INSERT INTO routes
    (path, methods, type, handler, handler_method, path_params, content_type, description,
     sort_order, is_default)
VALUES
    ('/api/users/{userId}/profile', 'GET', 'handler', 'UserHandler', 'getProfile', 'userId',
     'application/json', 'Get user profile', 15, TRUE);
```

Default routes cannot be edited or deleted at run time. For a route you want to
change later, leave `is_default` false and write it through `TableRecord`.

## 2. Write the handler

Handlers are singletons. The method takes the request and the response. Path
parameters are request attributes named `pathParam.{name}`.

```java
public class UserHandler {
  private static UserHandler instance;

  public static synchronized UserHandler getInstance() {
    if (instance == null) {
      instance = new UserHandler();
    }
    return instance;
  }

  public void getProfile(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String userId = (String) request.getAttribute("pathParam.userId");
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

Routes match in `sort_order`, then `id`, and the first match wins. A specific path
needs a lower `sort_order` than any wildcard that would match it.

```
# Correct
10  /api/attachment/{id}/download
20  /api/attachment/{id}
30  /api/**

# Wrong: the wildcard matches everything, the others never run
10  /api/**
20  /api/attachment/{id}/download
```

## Choosing a type

Pick `handler` when the endpoint streams, or when it needs the response object.
Pick `processor` when it returns a value and holds per-request state. A processor
cannot stream, because it returns before anything is written. Pick `script` when
JavaScript is enough. It needs no Java and no rebuild.

## Adding a processor instead

Same shape, two differences. Register in `getProcessorClassName` and
`getProcessorInstance`, and implement `IRequestProcessor` — startup validation
checks for that interface.

## Related

- [Route configuration](./route-configuration.md) — every field
- [Route registry](./route-registry.md) — validation and dispatch
- [Routing issues](../troubleshooting/routing-issues.md) — when it still 404s
