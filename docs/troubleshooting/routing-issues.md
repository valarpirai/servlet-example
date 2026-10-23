# Routing Issues

## New route returns 404

Work down this list.

1. Is there an active row for it in the `routes` table?
2. Does `methods` include the request method?
3. Is the path pattern right, `{param}` and `**` included?
4. Is its `sort_order` below any wildcard that would match first?
5. Is the name registered in `RouteRegistry`, in `getHandlerClassName` or
   `getProcessorClassName`?
6. Is the name registered in `RouteDispatcher`, in `getHandlerInstance` or
   `getProcessorInstance`?
7. Did 5 seconds pass? A changed row goes live on the next check. Look in the log for
   "Skipping route", which names a row that failed validation.

Steps 5 and 6 apply to Java handlers and processors. They are both required and
are the most common miss. Script routes need neither. See
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

```
# Wrong: nothing after this is reachable under /api
10  /api/**
20  /api/attachment/{id}/download
```

Nothing errors. The specific route simply never runs.

## Handler invocation failed

**Symptom.** The route matches but dispatch throws, often with
`ClassNotFoundException` or `NoSuchMethodException`.

**Cause.** The class name string in `RouteRegistry` does not resolve, or the
method named by `handler_method` does not exist with the expected signature.

These names are string literals, so a package rename or a method rename compiles
cleanly and breaks here. Check the literals first.

## A route was skipped

`RouteRegistry` validates every route as it loads. A table row that fails is
skipped, and the log says why: `Skipping route: Route id 42 (/x): ...`. A processor
must implement `IRequestProcessor`. A builtin must name `handleHealth` or
`handleMetrics`. A script route needs a script name and a function name.

A route in the deprecated `routes.yml` that fails stops startup.

Read the log before assuming a runtime problem.

## A route will not change

Default routes cannot be edited or deleted. The "Protect default routes" business
rule aborts the write, and `update` or `deleteRecord` returns false. Add a new
route with a lower `sort_order` instead. See [table routes](../routing/table-routes.md).

## Script route returns 500

`Handler function not found: name` means the `api_scripts` row has no function of
that name. Check `handler_method` against the script.

## Path parameter is null

The name must appear in `path_params`. A `{param}` in the path with no matching
`path_params` entry is not extracted.

## Related

- [Adding a route](../routing/adding-a-route.md) — the full checklist
- [Table routes](../routing/table-routes.md) — the routes table
- [Route configuration](../routing/route-configuration.md) — every field
- [Debugging](../development/debugging.md) — breakpoints and logs
