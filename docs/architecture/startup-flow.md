# Startup Flow

What happens between JVM start and the first accepted request.

The sequence has four stages. A static block configures logging and loads
database-backed properties. Flyway applies pending migrations. `Main` loads the
routes. Then it builds Tomcat and starts listening.

The app needs its database. Any failure before Tomcat starts ends the process with
exit code 1 and a `Cannot start:` message. There is no reduced mode.

```mermaid
flowchart TD
    A([JVM Start]) --> B

    subgraph STATIC["Main static block"]
        B[LoggingConfig.initialize\nHardcoded log defaults\nbootstrap only] --> C
        C[DbPropertiesLoader.initialize] --> D
    end

    D{db.url / username / password\npresent in application.yml?}
    D -->|No| X
    D -->|Yes| F

    subgraph FLYWAY["Flyway migration"]
        F[Flyway.configure\n.dataSource url,user,pass\n.locations classpath:db] --> G
        G[migrate\nApplies pending V__ migrations\nSkips already-applied versions]
    end

    G --> H{Database reachable\nand migration OK?}
    H -->|No| X
    H -->|Yes| J

    subgraph WIRE["Wire DB-backed properties"]
        J[new Database config\nHikariCP pool starts on first use\nMain shutdown hook closes it after Tomcat stops] --> K
        K[new PropertyRepository database] --> L
        L[PropertiesUtil.setPropertyRepository\nClears LRU cache]
    end

    L --> R0

    subgraph MAIN["Main.main()"]
        R0[RouteRegistry.getInstance\nLoads and validates the routes table] --> R1
        R1{Usable routes?}
        R1 -->|No| X
        R1 -->|Yes| M
        M[port = -Dserver.port, SERVER_PORT,\nthen app_properties server.port,\ndefault 8080] --> N
        N[new Tomcat port] --> O
        O[addServlet RouterServlet] --> P
        P[addFilter CorrelationIdFilter] --> R
        R[tomcat.start] --> T
        T[RuntimeMetrics.setWorkerExecutor\nso /metrics shows worker threads]
    end

    X[/"Print Cannot start: reason\nexit code 1"/]
    T --> S([Listening on :port])
```

## What stops startup

| Cause | Stage |
|---|---|
| `db.url`, `db.username` or `db.password` missing | `DbPropertiesLoader.initialize` |
| Database unreachable | Flyway, then the static block |
| A migration fails | Flyway |
| The `routes` table is unreadable, or has no usable routes | `RouteRegistry` in `Main.main` |

A route row that fails validation does not stop startup. The registry skips it and
logs the reason. Startup stops only when no route is left.

## Why routes load before Tomcat

`RouterServlet` has no load-on-startup flag, so Tomcat creates it on the first
request. Loading the registry in `Main` makes a bad table fail at start, not on the
first request. `RouterServlet.init` then picks up the loaded registry.

After startup the registry checks the table every 5 seconds. Edits go live without a
restart. See [table routes](../routing/table-routes.md).

## Why logging configures first

`LoggingConfig` runs before anything else and uses hardcoded defaults. It cannot
read properties, because properties are not loaded yet. This is bootstrap only.
Runtime log levels come from `logback.xml`.

## Related

- [Request flow](./request-flow.md) — what happens per request
- [Component responsibilities](./component-responsibilities.md) — who owns what
- [Configuration](./configuration.md) — credentials, pool size and property order
- [Properties lookup](../development/debugging.md) — cache, database, YAML order
