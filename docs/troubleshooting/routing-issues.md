# Routing Issues

## New route returns 404

Work down this list.

1. Is the route in `src/main/resources/routes.yml`?
2. Does the `method` match the request?
3. Is the path pattern right, `{param}` and `**` included?
4. Is it above any wildcard that would match first?
5. Is the name registered in `RouteRegistry`, in `getHandlerClassName` or
   `getProcessorClassName`?
6. Is the name registered in `RouteDispatcher`, in `getHandlerInstance` or
   `getProcessorInstance`?
7. Did the server restart after editing `routes.yml`?

Steps 5 and 6 are both required and are the most common miss. See
[route registry](../routing/route-registry.md).

```bash
mvn test -Dtest=RouteRegistryTest,RouteDispatcherTest
```

Breakpoints worth setting: `RouteRegistry.findRoute` to see whether the route
loaded at all, and `RouteDispatcher.dispatch` to see whether resolution
succeeded.

## Route order swallowed it

A wildcard placed above a specific route matches everything below it. First
match wins.

```yaml
# Wrong: nothing after this is reachable under /api
- path: /api/**
- path: /api/attachment/{id}/download
```

Nothing errors. The specific route simply never runs.

## Handler invocation failed

**Symptom.** The route matches but dispatch throws, often with
`ClassNotFoundException` or `NoSuchMethodException`.

**Cause.** The class name string in `RouteRegistry` does not resolve, or the
method named by `handlerMethod` does not exist with the expected signature.

These names are string literals, so a package rename or a method rename compiles
cleanly and breaks here. Check the literals first.

## Startup rejected a route

`RouteRegistry` validates every route as it loads and reports problems at
startup. A processor must implement `IRequestProcessor`. A builtin must name
`handleHealth` or `handleMetrics`.

Read the startup log before assuming a runtime problem.

## Path parameter is null

The name must appear under `pathParams`. A `{param}` in the path with no
matching `pathParams` entry is not extracted.

## Related

- [Adding a route](../routing/adding-a-route.md) — the full checklist
- [Route configuration](../routing/route-configuration.md) — every field
- [Debugging](../development/debugging.md) — breakpoints and logs
