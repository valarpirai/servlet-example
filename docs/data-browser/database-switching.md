# Database Switching

After connecting, a second dropdown lists the databases visible on that server.
Picking one switches the table browser.

The list comes from `POST /api/data-browser/databases`, which reads
`Connection.getMetaData().getCatalogs()`. That is every database the credentials
can see, not just the one named at connect time.

Selecting an entry calls `POST /api/data-browser/select-database`.

## Two mechanisms

Drivers disagree about whether a connection can change database, so
`supportsCatalogSwitch` picks the path.

**MySQL and Snowflake** switch in place. `handleSelectDatabase` calls
`Connection.setCatalog(database)`. Same connection, no reconnect.

**PostgreSQL** cannot. A connection is bound to one database for its lifetime.
`supportsCatalogSwitch` returns `false`, and the handler reconnects instead:

1. `PostgreSqlStrategy.buildUrlForDatabase` rebuilds the URL, swapping the
   database path segment
2. A new connection opens with the same credentials
3. `SessionManager.replaceConnection` swaps it in and closes the old one

The `sessionId` does not change. From the browser's side both paths look
identical.

## Why sessions hold credentials

The reconnect needs the original `dbType`, user, password and connect-time
properties, possibly long after `connect` returned. So `SessionManager.Session`
stores them alongside the `Connection` rather than just the connection.

This is the reason session state is larger than it first appears.

## Implementing it for a new database

If the driver supports `setCatalog`, do nothing. The default is correct.

If it does not, override `supportsCatalogSwitch` to return `false` and implement
`buildUrlForDatabase`. The default implementation throws
`UnsupportedOperationException`, so a missing override fails loudly on the first
switch rather than silently doing nothing.

## Related

- [DataSource strategies](./datasource-strategies.md) — the two methods involved
- [Data browser](./data-browser.md) — sessions and their lifetime
- [API endpoints](./data-browser-api.md) — databases and select-database
