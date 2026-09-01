# Design Patterns and Principles

A tour of the patterns this codebase actually uses. Written for a new team
member reading the source for the first time.

## Strategy: swap the implementation, keep the interface

`DataSourceStrategy` defines one method set. `PostgreSqlStrategy`,
`MySqlStrategy` and `SnowflakeStrategy` each implement it.
`DataSourceRegistry` holds no compile-time reference to any of them. See
[DataSource strategies](../data-browser/datasource-strategies.md) for the
`ServiceLoader` wiring.

`AttachmentStorage` is the same shape. `LocalFileSystemStorage` and
`DatabaseAttachmentStorage` implement it. `AttachmentManager` picks one at
startup and never checks which.

These two interfaces are the sanctioned places to add a backend without
touching an existing class.

## Singleton: a static field, no container

`RouteRegistry`, `AttachmentManager` and `DataSourceRegistry` are singletons.
Each holds a private static instance and a `getInstance()` method.
`DataSourceRegistry` builds its instance eagerly. `RouteRegistry` and
`AttachmentManager` build theirs lazily, behind a `synchronized` check.

There is no dependency injection framework here. No Spring, no Guice, no
`@Inject`. Every class that needs a singleton calls `getInstance()` directly.
Wiring is manual. Grep for `getInstance` to find every caller.

## Dispatch by name, not by type

`RouteDispatcher` reads a route's handler or processor name as a string. It
resolves the name by reflection: `handlerClass.getMethod("getInstance")` for
handlers, `IRequestProcessor.supports(contentType)` for processors. Its own
Javadoc calls it a bridge between `RouteRegistry` and the handler
implementations.

The tradeoff is explicit in the project CLAUDE.md: a rename compiles cleanly
and fails at runtime. The name lives in `routes.yml`, `RouteRegistry` and
`RouteDispatcher` as three separate string literals, not one symbol the
compiler can check.

## Builder: Lombok, not hand-rolled

`ProcessorResponse` uses Lombok's `@Builder` with `@Builder.Default` for
`statusCode` (200) and `contentType` (`application/json`). No processor
constructs a response with a bare constructor.

## Facade and adapter: one class absorbs a library's rough edges

- `Database` wraps jOOQ's `DSLContext` behind `query()`, `transact()` and
  `openConnection()`. Nothing else in the codebase imports jOOQ directly.
- `JsonUtil` wraps `Gson` with a custom `Instant` adapter. This is why
  `new Gson()` is banned elsewhere. See the project CLAUDE.md.
- `DriverShim` adapts a JDBC `Driver` loaded from a runtime `URLClassLoader`.
  `java.sql.DriverManager` only trusts drivers on the system classloader, so
  the shim is what makes a dynamically loaded driver usable.
- `MongoReflectionClient` calls the Mongo driver through `Class.forName` and
  `Method.invoke`. MongoDB has no JDK-standard interface like `java.sql.Driver`
  to shim against, so reflection is the only way to stay dependency-free.

## Filter chain for cross-cutting concerns

`CorrelationIdFilter` implements the standard `jakarta.servlet.Filter` chain.
It injects `X-Correlation-ID` into the SLF4J MDC before the request reaches
`RouterServlet`. Every log line in that request then carries the same ID. See
[Correlation IDs](../observability/correlation-ids.md).

## Principles enforced by structure, not convention

- **Package by function, not by layer.** `route/`, `processor/`, `handler/`,
  `storage/` and `datasource/` each hold one concern. See
  [Package map](./package-map.md).
- **Fail-soft configuration, fail-fast migrations.** A missing database falls
  through to `application.yml` and logs one warning. A changed Flyway
  migration throws on every startup, on every database. One risk is
  recoverable. The other corrupts history silently if allowed to pass.
- **Memory-bounded I/O is a constraint, not an optimization.**
  `AttachmentStorage` implementations never load a full file into memory. See
  [Memory guarantee](../storage/memory-guarantee.md). Heap scales with
  concurrent requests, not file size.
- **One property lookup, three levels.** LRU cache, then `app_properties`,
  then YAML. Every property read goes through `PropertiesUtil`. Nothing reads
  `application.yml` directly at runtime. See
  [Configuration](./configuration.md).

## Related

- [Component responsibilities](./component-responsibilities.md) — who owns what
- [Package map](./package-map.md) — where these classes live
- [DataSource strategies](../data-browser/datasource-strategies.md) — the SPI in detail
- [Configuration](./configuration.md) — the fallback chain in detail
