# Request Flow

How one HTTP request becomes a response.

Every request passes through the same funnel. A filter attaches a correlation
ID. `RouterServlet` wraps the call and times it. `RouteRegistry` matches method
and path. `RouteDispatcher` invokes whatever the route names.

Route type decides the rest. There are four.

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
    participant DB as Database (jOOQ)

    Client->>Tomcat: HTTP Request
    Tomcat->>CIF: doFilter(req, res)
    CIF->>CIF: Attach X-Correlation-ID header
    CIF->>RS: service(req, res)

    RS->>RS: handleRequest wrapper\nincrement counter, start timer
    RS->>RR: findRoute(method, path)

    alt No route matched
        RR-->>RS: null
        RS-->>Client: 404 { "error": "Not Found" }

    else Builtin route  /health  /metrics
        RR-->>RS: RouteMatch type=builtin
        RS->>RS: handleHealth() or handleMetrics()\ninline JSON response
        RS-->>Client: 200 { uptime, memory, … }

    else Static resource  /  /script-editor  /data-browser
        RR-->>RS: RouteMatch type=static
        RS->>RD: dispatch(match, req, res)
        RD->>RD: isValidResourcePath check\nreject traversal / absolute paths
        RD->>RD: getResourceAsStream classpath
        RD-->>Client: 200 text/html (streamed)

    else Handler route  /api/properties/**  /api/attachments/**
        RR-->>RS: RouteMatch type=handler\n+ pathParams map
        RS->>RD: dispatch(match, req, res)
        RD->>H: reflection: getInstance().method(req, res [,id])
        H->>DB: database.query(dsl → …) or transact(…)
        DB-->>H: Result / AppProperty / Attachment
        H-->>Client: 200/201/404 JSON

    else Processor route  /api/v1/**  /api/upload  /api/script
        RR-->>RS: RouteMatch type=processor
        RS->>RD: dispatch(match, req, res)
        RD->>H: new Processor().process(req)
        H-->>RD: ProcessorResponse (status, headers, body)
        RD-->>Client: HTTP response

    end

    RS->>RS: logRequest (method, path, status, ms)
```

## Handlers and processors differ

A handler is a singleton. `RouteDispatcher` calls `getInstance()` and invokes a
named method with the request, the response, and any path parameters. The
handler writes to the response itself.

A processor is constructed per request. It returns a `ProcessorResponse`, and
the dispatcher writes it. Processors never touch the response object.

That difference matters for downloads. A handler streaming a file owns the
output stream. A processor cannot stream, because it returns a value.

## Related

- [Startup flow](./startup-flow.md) — how routes get loaded
- [Route registry](../routing/route-registry.md) — matching and dispatch
- [Adding a route](../routing/adding-a-route.md) — picking a route type
- [Correlation IDs](../observability/correlation-ids.md) — request tracing
