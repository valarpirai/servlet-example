# Route Registry

How a method and path become a handler call.

Routes live in the `routes` table. `RouteRegistry` loads them, validates them, and
matches incoming requests. `RouteDispatcher` invokes whatever the matched route
names. See [table routes](./table-routes.md).

```
routes table
    ↓ loaded at startup, reloaded when a row changes
RouteRegistry      validates, compiles patterns, matches method and path
    ↓ RouteMatch
RouteDispatcher    resolves the target, invokes by reflection
    ↓
Handler or Processor
```

## Matching

`RouteRegistry` is a singleton. It compiles each path pattern to a regex when a
route loads, then scans routes in `sort_order` on every request.

```java
RouteRegistry registry = RouteRegistry.getInstance();
RouteRegistry.RouteMatch match = registry.findRoute("GET", "/api/attachment/abc-123/download");

if (match != null) {
    Route route = match.getRoute();
    String id = match.getPathParam("id"); // "abc-123"
}
```

Two pattern forms are supported. `{name}` captures one path segment and becomes
a path parameter. `**` matches all remaining segments and captures nothing.

First match wins. `sort_order` is significant — see
[adding a route](./adding-a-route.md).

## Validation

`RouteRegistry` checks every route as it loads. It resolves each handler and
processor name to a class, confirms the class exists, and confirms the named
method is present. A processor must implement `IRequestProcessor`. Builtin
routes must name `handleHealth` or `handleMetrics`. A script route needs a script
name and a function name.

A table row that fails is skipped and logged, and the other routes load. A route
in the deprecated `routes.yml` that fails stops startup.

## Names are registered in two places

A Java handler or processor name from a route is resolved twice, by two
different classes, for two different reasons.

| Class | Method | Purpose |
|---|---|---|
| `RouteRegistry` | `getHandlerClassName` | Startup validation, name to class name |
| `RouteRegistry` | `getProcessorClassName` | Startup validation, name to class name |
| `RouteDispatcher` | `getHandlerInstance` | Dispatch, returns the singleton |
| `RouteDispatcher` | `getProcessorInstance` | Dispatch, constructs a new instance |

Both sides need the new name. Register only in `RouteRegistry` and the route
validates but fails to dispatch. Register only in `RouteDispatcher` and validation
skips the route. Script routes need neither. They name a stored script.

The class names in `RouteRegistry` are string literals. A package rename must
update them, and the compiler will not warn you.

## Performance

Loading is linear in route count. It happens at startup and when the table
changes. The check for a change is one small query every 5 seconds, not one per
request. Matching is a linear scan per request against precompiled patterns. With a
few dozen routes this is not worth optimizing.

## Related

- [Table routes](./table-routes.md) — the routes table, refresh and default routes
- [Route configuration](./route-configuration.md) — the route fields
- [Adding a route](./adding-a-route.md) — the full checklist
- [Request flow](../architecture/request-flow.md) — where routing sits
- [Routing issues](../troubleshooting/routing-issues.md) — when a route 404s
