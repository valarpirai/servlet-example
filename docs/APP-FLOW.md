# Application Flow Diagrams

## App Startup Flow

```mermaid
flowchart TD
    A([JVM Start]) --> B

    subgraph STATIC["Main static block"]
        B[LoggingConfig.initialize\nHardcoded log defaults\nbootstrap only] --> C
        C[DbPropertiesLoader.initialize] --> D
    end

    D{db.url / username / password\npresent in application.yml?}
    D -->|No| E[/"⚠ Log warning\nYAML-only mode"/]
    D -->|Yes| F

    subgraph FLYWAY["Flyway migration"]
        F[Flyway.configure\n.dataSource url,user,pass\n.locations classpath:db] --> G
        G[migrate\nApplies V1__create_app_properties\nV2__seed_app_properties\nV3__create_attachments\nSkips already-applied versions]
    end

    G --> H{Migration OK?}
    H -->|Error| I[/"⚠ Log error\nFallback to YAML defaults"/]
    H -->|OK| J

    subgraph WIRE["Wire DB-backed properties"]
        J[new Database config] --> K
        K[new PropertyRepository database] --> L
        L[PropertiesUtil.setPropertyRepository\nClears LRU cache]
    end

    E --> M
    I --> M
    L --> M

    subgraph MAIN["Main.main()"]
        M[port = PropertiesUtil.getInt\nserver.port default 8080] --> N
        N[new Tomcat port] --> O
        O[addServlet RouterServlet] --> P
        P[addFilter CorrelationIdFilter] --> Q
        Q["RouterServlet.init()\nRouteRegistry.getInstance()\nLoads routes.yml → 21 routes validated\nnew RouteDispatcher()"] --> R
        R[tomcat.start]
    end

    R --> S([Listening on :port])
```

---

## HTTP Request Flow

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

---

## Component Responsibilities

| Layer | Class | Role |
|---|---|---|
| Entry point | `Main` | Starts Tomcat, wires startup sequence |
| Bootstrap | `LoggingConfig` | Configures SLF4J/Logback with hardcoded defaults |
| Bootstrap | `DbPropertiesLoader` | Runs Flyway, creates `Database` + `PropertyRepository` |
| Properties | `PropertiesUtil` | LRU cache → DB → YAML three-level property lookup |
| Routing | `RouteRegistry` | Loads `routes.yml`, validates, matches method + path |
| Routing | `RouteDispatcher` | Resolves handler/processor by name, invokes via reflection |
| Servlet | `RouterServlet` | Wraps all requests; handles builtins inline |
| Filter | `CorrelationIdFilter` | Injects `X-Correlation-ID` for structured logging |
| DB wrapper | `Database` | `query()` / `transact()` / `openConnection()` over jOOQ |
| DB | `PropertyRepository` | CRUD for `app_properties` via jOOQ DSL |
| Storage | `DatabaseAttachmentStorage` | Chunk-streamed BYTEA storage; cursor-based retrieval |
| Storage | `LocalFileSystemStorage` | Filesystem fallback, 1 MB chunked writes |
| Handlers | `PropertiesHandler` `AttachmentHandler` `ApiHandler` | REST endpoints |
| Processors | `ScriptProcessor` `ModuleProcessor` `FileUploadProcessor` `TemplateProcessor` | Request processors |
```
