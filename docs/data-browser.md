# Data Browser

**Last updated**: 2026-08-05

A web-based database browser at `/data-browser`. Connect to PostgreSQL, MySQL, or Snowflake — JDBC drivers are downloaded on demand, no server restart required.

**Key files**:
- `datasource/DataSourceStrategy.java` - Strategy interface
- `datasource/DataSourceRegistry.java` - Strategy registry (singleton, discovers via `ServiceLoader`)
- `resources/META-INF/services/com.example.datasource.DataSourceStrategy` - Strategy discovery file, one FQCN per line
- `datasource/PostgreSqlStrategy.java` - PostgreSQL implementation
- `datasource/MySqlStrategy.java` - MySQL implementation
- `datasource/SnowflakeStrategy.java` - Snowflake implementation
- `handler/DataBrowserHandler.java` - Session management, query execution, database switching
- `extlib/SessionManager.java` - Per-session connection + connect-time credentials, needed to reconnect on database switch
- `src/main/resources/static/data-browser.html` - UI

**Tests**: `DataSourceRegistryTest`, `PostgreSqlStrategyTest`, `MySqlStrategyTest`, `SnowflakeStrategyTest`, `SessionManagerTest`, `DataBrowserHandlerTest`

## Quick Start

1. Start the server: `mvn -PappRun`
2. Open `http://localhost:8080/data-browser`
3. Select a database type, fill in connection details
4. Click **↓ Download Driver** if the driver badge shows "not downloaded"
5. Click **Connect**, then browse tables or run SQL queries

## Supported Databases

| Type | JDBC URL format |
|---|---|
| PostgreSQL | `jdbc:postgresql://host:5432/dbname` |
| MySQL | `jdbc:mysql://host:3306/dbname` |
| Snowflake | Account-based fields (URL auto-built) |

Snowflake-specific fields: **Account**, Warehouse, Database, Schema, Role (optional fields shown dynamically in the UI).

## Architecture

### DataSource Strategy Pattern

All per-database behaviour is encapsulated in a `DataSourceStrategy` implementation. The `DataSourceRegistry` singleton discovers strategies via `ServiceLoader` from `META-INF/services/com.example.datasource.DataSourceStrategy` — no code change needed to register a new one.

```
com.example.datasource/
├── DataSourceStrategy.java     # Interface
├── DataSourceRegistry.java     # Singleton registry
├── PostgreSqlStrategy.java
├── MySqlStrategy.java
└── SnowflakeStrategy.java
```

Each strategy provides:
- `getDbType()` — short key (`"postgresql"`, `"mysql"`, `"snowflake"`)
- `getDisplayName()` — human-readable name shown in the UI's database-type dropdown, e.g. `"PostgreSQL"`
- `getJarPath()` — Maven Central path for the JDBC JAR
- `getDriverClass()` — fully-qualified driver class name
- `buildUrl(Map<String,String> props)` — assembles JDBC URL from user input
- `buildConnectionProperties(user, password, props)` — builds `java.util.Properties` (adds Snowflake extras: warehouse, db, schema, role)
- `getSystemSchemas()` — schemas hidden from the table browser
- `supportsCatalogSwitch()` — default `true`; whether the active database can change on an existing connection via `Connection.setCatalog(...)`. PostgreSQL overrides this to `false`
- `buildUrlForDatabase(originalProps, database)` — only called when `supportsCatalogSwitch()` is `false`; rebuilds the connection URL for a different database. PostgreSQL overrides this to swap the database path segment; the default implementation throws `UnsupportedOperationException`

### Adding a New Database

1. Create `src/main/java/com/example/datasource/YourDbStrategy.java` implementing `DataSourceStrategy` (needs a public no-arg constructor)
2. Add its fully-qualified name as a new line in `src/main/resources/META-INF/services/com.example.datasource.DataSourceStrategy`
3. Add a row to the driver table in [How Driver Loading Works](#how-driver-loading-works) below, matching the JAR/version returned by `getJarPath()`
4. Add connection fields to `DB_FIELDS` in `data-browser.html` — the database-*type* dropdown itself needs no HTML edit; it's populated at runtime from `/api/data-browser/db-types`
5. If the driver doesn't support `Connection.setCatalog(...)` (PostgreSQL-style, one database per connection), override `supportsCatalogSwitch()` to return `false` and implement `buildUrlForDatabase(...)`; otherwise the default (`true`) is correct and no override is needed
6. Add `YourDbStrategyTest` alongside the existing strategy tests

No changes needed in `DataSourceRegistry`, `ExtLibManager`, or `DataBrowserHandler`. Manual registration via `DataSourceRegistry.register(...)` still works and takes precedence for test doubles or runtime overrides.

**Gotchas:**
- If a class listed in the services file is missing, lacks a public no-arg constructor, or doesn't implement `DataSourceStrategy`, `ServiceLoader` throws `ServiceConfigurationError` when `DataSourceRegistry` first initializes (app startup / first use) — not at compile time. Double-check the FQCN before committing.
- If two discovered strategies return the same `getDbType()`, the last one registered silently wins (`Map.put` semantics) — there's no duplicate-key check. "Last" is decided by `ServiceLoader` iteration order, i.e. the order of lines in the services file.

### Discovery: manual registration vs `ServiceLoader`

Two ways to wire a `DataSourceStrategy` into the registry:

| | Manual registration | `ServiceLoader` (current) |
|---|---|---|
| How | `DataSourceRegistry` constructor calls `register(new XStrategy())` for each type | Registry calls `ServiceLoader.load(DataSourceStrategy.class)`; strategies list themselves in `META-INF/services/com.example.datasource.DataSourceStrategy` |
| Adding a strategy | Edit `DataSourceRegistry.java` + implement the interface | Implement the interface + add one line to the services file — registry untouched |
| Coupling | Registry imports every strategy class directly | Registry has no compile-time reference to any strategy |
| Discoverability | Easy to `Cmd+click` from registry to implementation | Requires knowing to check the services file |

We chose `ServiceLoader` because it removes the one place every new database previously had to touch (`DataSourceRegistry`), which matters as more strategies get added — new database support becomes purely additive (new class + one service-file line), with no risk of merge conflicts in a shared registry file. The tradeoff — a service file that's less obvious than a constructor call — is minor at this scale and is mitigated by keeping `register()` public, so tests and runtime code can still register or override a strategy directly without touching the services file.

### How Driver Loading Works

Drivers are downloaded from Maven Central to `extlib/` on first use and reused on subsequent connections. No restart needed — they are loaded into a `URLClassLoader` at runtime and registered with `DriverManager` via a `DriverShim`.

| Driver | JAR | Version |
|---|---|---|
| PostgreSQL | `postgresql` | 42.7.3 |
| MySQL | `mysql-connector-j` | 8.4.0 |
| Snowflake | `snowflake-jdbc` | 3.15.0 |

### Database Type Dropdown

The **Database Type** dropdown (PostgreSQL/MySQL/Snowflake) is populated at page load from `GET /api/data-browser/db-types`, which lists every `DataSourceRegistry`-discovered strategy's `getDbType()`/`getDisplayName()`. Adding a database via [Adding a New Database](#adding-a-new-database) makes it appear in this dropdown automatically — no HTML change needed.

### Switching Databases

After connecting, a second **Database** dropdown appears, populated from `POST /api/data-browser/databases` (`Connection.getMetaData().getCatalogs()` — the actual databases visible on that server, not just the one you connected to). Picking a different entry calls `POST /api/data-browser/select-database`, and the table browser refreshes to the new database.

The two supported drivers behave differently under the hood:
- **MySQL / Snowflake**: the active database is switched in place via `Connection.setCatalog(database)` — no reconnect.
- **PostgreSQL**: a connection is bound to one database for its lifetime, so there is no in-connection way to switch. `handleSelectDatabase` instead reconnects transparently: it rebuilds the JDBC URL by swapping the database path segment (`PostgreSqlStrategy.buildUrlForDatabase`), opens a new connection with the same credentials, and replaces the session's connection (`SessionManager.replaceConnection`) — the old connection is closed and the `sessionId` stays the same.

This is why `SessionManager.Session` stores the original `dbType`/`user`/`password`/connect-time `props` alongside the connection, not just the `Connection` object: PostgreSQL's reconnect needs them to rebuild a new connection later, potentially long after the initial `connect` call.

## API Endpoints

All endpoints accept and return `application/json`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/data-browser/db-types` | List registered database types for the connection-form dropdown |
| `POST` | `/api/data-browser/driver-status` | Check if driver JAR is downloaded |
| `POST` | `/api/data-browser/download` | Download + load JDBC driver |
| `POST` | `/api/data-browser/connect` | Open connection, returns `sessionId` |
| `POST` | `/api/data-browser/databases` | List actual databases on the connected server |
| `POST` | `/api/data-browser/select-database` | Switch the session's active database |
| `POST` | `/api/data-browser/tables` | List schemas and tables |
| `POST` | `/api/data-browser/query` | Execute SQL, paginated (100 rows/page) |
| `POST` | `/api/data-browser/disconnect` | Close connection |

### Example: Check driver status

```bash
curl -X POST http://localhost:8080/api/data-browser/driver-status \
  -H "Content-Type: application/json" \
  -d '{"dbType":"postgresql"}'
```

Response: `{"status":"success","data":{"dbType":"postgresql","downloaded":true}}`

### Example: Connect

```bash
curl -X POST http://localhost:8080/api/data-browser/connect \
  -H "Content-Type: application/json" \
  -d '{"dbType":"postgresql","url":"jdbc:postgresql://localhost:5432/mydb","user":"alice","password":"secret"}'
```

Response: `{"status":"success","data":{"sessionId":"<uuid>","status":"connected"}}`

### Example: List databases

```bash
curl -X POST http://localhost:8080/api/data-browser/databases \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<uuid>"}'
```

Response: `{"status":"success","data":{"databases":["appdb","otherdb"],"current":"appdb"}}`

### Example: Switch database

```bash
curl -X POST http://localhost:8080/api/data-browser/select-database \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<uuid>","database":"otherdb"}'
```

Response: `{"status":"success","data":{"database":"otherdb"}}`

### Example: Query

```bash
curl -X POST http://localhost:8080/api/data-browser/query \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<uuid>","sql":"SELECT * FROM users","page":1}'
```

## Testing

Unit tests cover the datasource strategy layer, session handling, and the new endpoints (JUnit 5 + Mockito):

| Test Class | Coverage |
|---|---|
| `DataSourceRegistryTest` | Singleton lookup, case-insensitive get, unknown types, `ServiceLoader` discovery independent of thread context classloader |
| `PostgreSqlStrategyTest` | URL pass-through, validation, system schemas, display name, `buildUrlForDatabase` URL rewriting |
| `MySqlStrategyTest` | URL pass-through, validation, system schemas, display name, catalog-switch support |
| `SnowflakeStrategyTest` | URL assembly, optional fields, blank-field skipping, display name, catalog-switch support |
| `SessionManagerTest` | Session creation/lookup, connection replacement (and old-connection closing), removal |
| `DataBrowserHandlerTest` | `db-types`, `databases`, `select-database` — success, session-expired, unknown-dbType paths (PostgreSQL's reconnect branch is not covered; see class Javadoc) |
| `TemplateEngineTest` | Variable substitution, XSS escaping, for loops, dot notation |
| `JsonUtilTest` | Response shapes, JSON roundtrip, `isValidJson` |

Run tests:

```bash
mvn test
```

## Sessions

- Sessions live server-side, keyed by UUID `sessionId`
- TTL: **30 minutes** of inactivity, then auto-closed
- Credentials are stored in browser `localStorage` only — never persisted server-side
- On page reload, credentials are restored from `localStorage` and a new session is established

## UI Features

- **Driver status badge** — shows whether the driver is downloaded; click **↓ Download Driver** if not
- **Table Browser tab** — expandable schema/table tree; click a table to query its rows
- **SQL Editor tab** — freeform SQL with `Cmd+Enter` / `Ctrl+Enter` shortcut to run
- **Results grid** — paginated (Prev / Next), handles nulls and binary columns (Base64)
