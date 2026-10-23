# Startup Flow

What happens between JVM start and the first accepted request.

The sequence has three stages. A static block configures logging and loads
database-backed properties. Flyway applies pending migrations. Then `Main`
builds Tomcat and starts listening.

Each stage degrades instead of failing. Missing database settings drop the app
into YAML-only mode. A failed migration falls back to YAML defaults. The server
still starts.

```mermaid
flowchart TD
    A([JVM Start]) --> B

    subgraph STATIC["Main static block"]
        B[LoggingConfig.initialize\nHardcoded log defaults\nbootstrap only] --> C
        C[DbPropertiesLoader.initialize] --> D
    end

    D{db.url / username / password\npresent in application.yml?}
    D -->|No| E[/"Log warning\nYAML-only mode"/]
    D -->|Yes| F

    subgraph FLYWAY["Flyway migration"]
        F[Flyway.configure\n.dataSource url,user,pass\n.locations classpath:db] --> G
        G[migrate\nApplies pending V__ migrations\nSkips already-applied versions]
    end

    G --> H{Migration OK?}
    H -->|Error| I[/"Log error\nFallback to YAML defaults"/]
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
        Q["RouterServlet.init()\nRouteRegistry.getInstance()\nLoads routes from the table, validates routes\nnew RouteDispatcher()"] --> R
        R[tomcat.start]
    end

    R --> S([Listening on :port])
```

## Why logging configures first

`LoggingConfig` runs before anything else and uses hardcoded defaults. It cannot
read properties, because properties are not loaded yet. This is bootstrap only.
Runtime log levels come from `logback.xml`.

## Related

- [Request flow](./request-flow.md) — what happens per request
- [Component responsibilities](./component-responsibilities.md) — who owns what
- [Properties lookup](../development/debugging.md) — cache, database, YAML order
