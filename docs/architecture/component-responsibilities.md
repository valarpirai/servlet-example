# Component Responsibilities

Who owns what. One row per class that carries real responsibility.

| Layer | Class | Role |
|---|---|---|
| Entry point | `Main` | Starts Tomcat, wires startup sequence |
| Bootstrap | `LoggingConfig` | Configures SLF4J and Logback with hardcoded defaults |
| Bootstrap | `DbPropertiesLoader` | Runs Flyway, creates `Database` and `PropertyRepository` |
| Properties | `PropertiesUtil` | LRU cache, then database, then YAML lookup |
| Routing | `RouteRegistry` | Loads `routes.yml`, validates, matches method and path |
| Routing | `RouteDispatcher` | Resolves handler or processor by name, invokes by reflection |
| Servlet | `RouterServlet` | Wraps all requests, handles builtins inline |
| Filter | `CorrelationIdFilter` | Injects `X-Correlation-ID` for structured logging |
| DB wrapper | `Database` | `query()`, `transact()`, `openConnection()` over jOOQ |
| DB | `PropertyRepository` | CRUD for `app_properties` through the jOOQ DSL |
| Storage | `AttachmentManager` | Coordinates storage backends, caches metadata |
| Storage | `LocalFileSystemStorage` | Filesystem backend, chunked writes |
| Storage | `DatabaseAttachmentStorage` | Chunk-streamed BYTEA storage, cursor retrieval |
| Scripting | `ScriptExecutor` | Runs script source inside a Rhino context |
| Scripting | `ScriptScopeBuilder` | Builds the sandbox scope handed to a script |
| Scripting | `ScriptModuleLoader` | Resolves `require()` and import calls |
| Scripting | `ScriptSecurityManager` | ClassShutter whitelist and blacklist |
| Data browser | `DataSourceRegistry` | Discovers strategies through `ServiceLoader` |
| Data browser | `ExtLibManager` | Loads JDBC driver jars at runtime |

## Singletons and why

`RouteRegistry`, `AttachmentManager`, `DataSourceRegistry` and every handler are
singletons. They hold loaded configuration or a cache, and building them per
request would throw that away.

Processors are not singletons. They hold per-request state, so a shared instance
would be a race.

## The three-level property lookup

`PropertiesUtil` answers every property question in the same order:

1. LRU cache
2. `app_properties` table, when a database is configured
3. `application.yml`

Environment variables override YAML values. A missing database is not an error.
The lookup falls through to YAML and logs a warning once at startup.

## Related

- [Startup flow](./startup-flow.md) — the wiring order
- [Request flow](./request-flow.md) — the per-request path
- [Package map](./package-map.md) — where these classes live
