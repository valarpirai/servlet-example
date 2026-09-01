# Database Issues

## JDBC driver not found

**Cause.** The driver jar has not been downloaded yet.

Click **Download Driver** in the UI, or:

```bash
curl -X POST http://localhost:8080/api/data-browser/download \
  -H "Content-Type: application/json" \
  -d '{"dbType":"postgresql"}'
```

Confirm the jar landed in `data/extlib/`. See
[driver loading](../data-browser/driver-loading.md).

## Session expired

Reconnect. Session lifetime and credential handling are described in
[data browser](../data-browser/data-browser.md).

## Unknown database type

**Cause.** The `dbType` does not match any registered strategy.

```bash
curl localhost:8080/api/data-browser/db-types
```

If a new strategy is missing from that list, its fully qualified name is not in
`META-INF/services/com.example.datasource.DataSourceStrategy`, or the class
lacks a public no-arg constructor.

## ServiceConfigurationError at startup

**Cause.** A class listed in the services file is missing, has no public no-arg
constructor, or does not implement `DataSourceStrategy`.

This surfaces when `DataSourceRegistry` first initializes, not at compile time.
Check the spelling of the fully qualified name.

## Two strategies, one wins silently

Two strategies returning the same `getDbType` collide. The last registered wins,
following `Map.put`, ordered by the service file. There is no duplicate check.

See [DataSource strategies](../data-browser/datasource-strategies.md).

## Database switch did nothing

PostgreSQL cannot switch database on an open connection, so
`handleSelectDatabase` reconnects instead. That needs
`supportsCatalogSwitch` returning `false` and a working
`buildUrlForDatabase`.

A strategy that returns `true` but cannot actually switch fails quietly. One
that returns `false` without implementing `buildUrlForDatabase` throws
`UnsupportedOperationException`.

See [database switching](../data-browser/database-switching.md).

## Mongo connect fails

`MongoHandler.handleConnect` downloads three driver jars on first use, so it
needs network access. It is also the one Mongo endpoint with no automated test.

If a find returns nothing unexpectedly, check the filter syntax against
[MongoDB mode](../data-browser/mongodb-mode.md).

## Related

- [Data browser](../data-browser/data-browser.md) — the feature and sessions
- [API endpoints](../data-browser/data-browser-api.md) — the HTTP surface
- [Runtime issues](./runtime-issues.md) — startup and memory
