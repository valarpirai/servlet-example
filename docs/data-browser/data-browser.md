# Data Browser

A web database browser at `/data-browser`.

It connects to PostgreSQL, MySQL or Snowflake over JDBC, with drivers downloaded
on demand and no restart. MongoDB is supported through a separate non-JDBC path,
see [MongoDB mode](./mongodb-mode.md).

## Quick start

1. Start the server with `mvn -PappRun`
2. Open `http://localhost:8080/data-browser`
3. Pick a database type and fill in the connection fields
4. Click **Download Driver** if the badge says the driver is missing
5. Click **Connect**, then browse tables or run SQL

## Supported databases

| Type | JDBC URL |
|---|---|
| PostgreSQL | `jdbc:postgresql://host:5432/dbname` |
| MySQL | `jdbc:mysql://host:3306/dbname` |
| Snowflake | Built from account fields |

Snowflake takes Account, Warehouse, Database, Schema and Role instead of a URL.
The UI shows those fields only when Snowflake is selected.

## Sessions

Sessions are server-side, keyed by a UUID `sessionId`. They expire after 30
minutes of inactivity and the connection is closed.

Credentials are never stored server-side. The browser keeps them in
`localStorage`, and a page reload re-establishes a session from there.

`SessionManager.Session` holds the connection plus the `dbType`, user, password
and connect-time properties. That extra state exists because PostgreSQL needs to
rebuild a connection later, see [database switching](./database-switching.md).

## UI

**Driver status badge** shows whether the JDBC jar is present, with a download
button when it is not.

**Table Browser** is an expandable schema and table tree. Clicking a table
queries its rows.

**SQL Editor** runs freeform SQL. `Cmd+Enter` or `Ctrl+Enter` executes.

**Results grid** pages 100 rows at a time and renders nulls and binary columns,
the latter as Base64.

Selecting MongoDB swaps the sidebar into a collection browser and a find query
tab, and renders one JSON document per row.

## Related

- [DataSource strategies](./datasource-strategies.md) — adding a database type
- [Driver loading](./driver-loading.md) — how jars arrive at runtime
- [Database switching](./database-switching.md) — changing database after connecting
- [MongoDB mode](./mongodb-mode.md) — the non-JDBC path
- [API endpoints](./data-browser-api.md) — the HTTP surface
