# Request Flow

How one HTTP request becomes a response.

Every request passes through the same funnel. A filter attaches a correlation
ID. `RouterServlet` wraps the call and times it. `RouteRegistry` matches method
and path. `RouteDispatcher` invokes whatever the route names.

Route type decides the rest. There are five.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Tomcat
    participant CIF as CorrelationIdFilter
    participant RS as RouterServlet
    participant RR as RouteRegistry
    participant RD as RouteDispatcher
    participant H as Handler / Processor
    participant DB as Database (jOOQ, pooled)

    Client->>Tomcat: HTTP Request
    Tomcat->>CIF: doFilter(req, res)
    CIF->>CIF: Attach X-Correlation-ID header
    CIF->>RS: service(req, res)

    RS->>RS: handleRequest wrapper\nincrement counter, start timer
    RS->>RR: findRoute(method, path)
    opt 5 seconds since the last check
        RR->>DB: routes table fingerprint
        DB-->>RR: reload only when it changed
    end

    alt No route matched
        RR-->>RS: null
        RS-->>Client: 404 { "error": "Not Found" }

    else Builtin route  /health  /metrics
        RR-->>RS: RouteMatch type=builtin
        RS->>RS: handleHealth() or handleMetrics()\ninline JSON response
        RS-->>Client: 200 { uptime } or { memory, threads, workers, db }

    else Static resource  /  /script-editor  /tables
        RR-->>RS: RouteMatch type=static
        RS->>RD: dispatch(match, req, res)
        RD->>RD: isValidResourcePath check\nreject traversal / absolute paths
        RD->>RD: getResourceAsStream classpath
        RD-->>Client: 200 text/html (streamed)

    else Handler route  /api/tables/**  /api/attachments/**  /api/v1/**
        RR-->>RS: RouteMatch type=handler\n+ pathParams map
        RS->>RD: dispatch(match, req, res)
        RD->>H: reflection: getInstance().method(req, res)
        H->>DB: database.query(dsl → …) or TableRecord
        DB-->>H: Result / Row / Attachment
        H-->>Client: 200/201/404 JSON

    else Processor route  /api/upload  /api/script
        RR-->>RS: RouteMatch type=processor
        RS->>RD: dispatch(match, req, res)
        RD->>H: new Processor().process(req)
        H-->>RD: ProcessorResponse (status, headers, body)
        RD-->>Client: HTTP response

    else Script route  /hello/{who}  /api/modules/**
        RR-->>RS: RouteMatch type=script\n+ pathParams map
        RS->>RD: dispatch(match, req, res)
        RD->>H: ApiHandler runs the api_scripts row\ncalls handler_method(request, response)
        H->>DB: read script and imported js_modules
        H-->>Client: status, headers and body the script set
    end

    RS->>RS: logRequest (method, path, status, ms)
```

## Handlers and processors differ

A handler is a singleton. `RouteDispatcher` calls `getInstance()` and invokes a
named method with the request and the response. Path parameters sit in request
attributes named `pathParam.<name>`. The handler writes to the response itself.

A processor is constructed per request. It returns a `ProcessorResponse`, and
the dispatcher writes it. Processors never touch the response object.

That difference matters for downloads. A handler streaming a file owns the
output stream. A processor cannot stream, because it returns a value.

## Script routes

A script route names an `api_scripts` row and a function in it. The dispatcher hands
both to `ApiHandler`, which runs the script in the Rhino sandbox. The function
receives a `request` object (`method`, `path`, `pathParams`, `queryParams`,
`headers`, `body`) and a `response` object. A missing function returns 500.

## Writes and business rules

A write through `TableRecord` runs the `before` rules, then the write, then the
`after` rules. A `before` rule can stop the write. Handlers, scripts and the table
browser share this path. See
[table records and rules](../scripting/table-records-and-rules.md).

## Related

- [Startup flow](./startup-flow.md) — how routes get loaded
- [Route registry](../routing/route-registry.md) — matching and dispatch
- [Table routes](../routing/table-routes.md) — the routes table and script routes
- [Adding a route](../routing/adding-a-route.md) — picking a route type
- [Correlation IDs](../observability/correlation-ids.md) — request tracing
