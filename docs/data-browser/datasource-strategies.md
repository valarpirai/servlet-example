# DataSource Strategies

One class per database type. Everything database-specific lives there.

`DataSourceRegistry` is a singleton that finds strategies through
`ServiceLoader`, reading
`META-INF/services/com.example.datasource.DataSourceStrategy`. This is Java's
Service Provider Interface (SPI) mechanism. The registry has no compile-time
reference to any strategy, so adding a database touches no existing Java file.

## The interface

| Method | Returns |
|---|---|
| `getDbType` | Short key, such as `postgresql` |
| `getDisplayName` | Label for the UI dropdown, such as `PostgreSQL` |
| `getJarPath` | Maven Central path to the JDBC jar |
| `getDriverClass` | Fully qualified driver class name |
| `buildUrl` | JDBC URL assembled from user input |
| `buildConnectionProperties` | `Properties`, including vendor extras |
| `getSystemSchemas` | Schemas hidden from the table browser |
| `supportsCatalogSwitch` | Whether `setCatalog` can change database in place |
| `buildUrlForDatabase` | Rebuilds a URL for another database |

`supportsCatalogSwitch` defaults to `true`. `buildUrlForDatabase` is only called
when it returns `false`, and the default implementation throws
`UnsupportedOperationException`.

## Adding a database

1. Write `datasource/YourDbStrategy.java` implementing `DataSourceStrategy`, with
   a public no-arg constructor
2. Add its fully qualified name as a line in
   `META-INF/services/com.example.datasource.DataSourceStrategy`
3. Add connection fields to `DB_FIELDS` in `static/data-browser.html`
4. Override `supportsCatalogSwitch` and implement `buildUrlForDatabase` if a
   connection is bound to one database
5. Add `YourDbStrategyTest` beside the other strategy tests

The type dropdown needs no HTML change. It is populated at runtime from
`/api/data-browser/db-types`.

Nothing in `DataSourceRegistry`, `ExtLibManager` or `DataBrowserHandler` changes.

## Why ServiceLoader

New database support is purely additive: a new class and one service-file line.
Nothing shared gets edited, so two people adding two databases cannot conflict.

The cost is discoverability. A service file is less obvious than a constructor
call, and you cannot jump from registry to implementation in an IDE.
`DataSourceRegistry.register` stays public so tests and runtime code can still
register or override a strategy directly.

## Two ways this bites

A class listed in the services file that is missing, lacks a public no-arg
constructor, or does not implement the interface throws
`ServiceConfigurationError` when the registry first initializes. That is startup
or first use, not compile time. Check the name before committing.

Two strategies returning the same `getDbType` silently collide. The last one
registered wins, following `Map.put`, and order comes from the service file. No
duplicate check exists.

## Related

- [Driver loading](./driver-loading.md) — what getJarPath feeds
- [Database switching](./database-switching.md) — the catalog-switch methods
- [Choosing an integration](./choosing-an-integration.md) — when a strategy is the wrong shape
- [Data browser](./data-browser.md) — the feature overview
