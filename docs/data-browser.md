# Data Browser

**Last updated**: 2026-08-06

A web-based database browser at `/data-browser`. Connect to PostgreSQL, MySQL, or Snowflake via JDBC (drivers downloaded on demand, no server restart required), or to plain MongoDB via a separate non-JDBC mode (see [MongoDB Browser Mode](#mongodb-browser-mode)).

**Key files**:
- `datasource/DataSourceStrategy.java` - Strategy interface
- `datasource/DataSourceRegistry.java` - Strategy registry (singleton, discovers via `ServiceLoader`)
- `resources/META-INF/services/com.example.datasource.DataSourceStrategy` - Strategy discovery file, one FQCN per line
- `datasource/PostgreSqlStrategy.java` - PostgreSQL implementation
- `datasource/MySqlStrategy.java` - MySQL implementation
- `datasource/SnowflakeStrategy.java` - Snowflake implementation
- `handler/DataBrowserHandler.java` - Session management, query execution, database switching
- `extlib/SessionManager.java` - Per-session connection + connect-time credentials, needed to reconnect on database switch
- `mongo/MongoExtLibManager.java` - Downloads the MongoDB driver jars to `extlib/` and loads them into a `URLClassLoader`
- `mongo/MongoReflectionClient.java` - Drives the loaded driver purely via reflection (no compile-time `com.mongodb.*`/`org.bson.*` dependency)
- `mongo/MongoSessionManager.java` - Per-session Mongo client handle
- `handler/MongoHandler.java` - Mongo connect/collections/find/disconnect endpoints
- `src/main/resources/static/data-browser.html` - UI (JDBC table-browser flow + Mongo collection-browser flow)

**Tests**: `DataSourceRegistryTest`, `PostgreSqlStrategyTest`, `MySqlStrategyTest`, `SnowflakeStrategyTest`, `SessionManagerTest`, `DataBrowserHandlerTest`, `MongoSessionManagerTest`, `MongoHandlerTest`

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

## MongoDB Browser Mode

Plain/self-hosted MongoDB doesn't fit the JDBC-based `DataSourceStrategy` architecture above — there is no JDBC driver, no `Connection`/`ResultSet`, and no tabular schema. Instead of forcing it into that shape, MongoDB support is a **separate, parallel path**: its own package (`com.example.mongo`), its own session manager, its own handler, and its own UI mode ("Collection Browser" + "Find Query" instead of "Table Browser" + "SQL Editor"). Selecting **MongoDB** in the Database Type dropdown switches the whole sidebar/tab UI into this mode; it is not a `DataSourceStrategy` and does not appear in `/api/data-browser/db-types`.

### Why reflection instead of `DriverShim`

JDBC drivers are loaded dynamically (downloaded to `extlib/`, loaded into a `URLClassLoader`) and still usable by our code because `java.sql.Driver`/`Connection`/`ResultSet` are **JDK-standard types**, loaded once by the platform classloader — a class loaded from a `URLClassLoader` that implements `java.sql.Driver` can be cast to `Driver` from anywhere, which is exactly what `DriverShim` relies on.

MongoDB's client API (`com.mongodb.client.MongoClient`, `MongoDatabase`, `MongoCollection`, ...) has no such JDK-standard equivalent to shim against. A class loaded from our `URLClassLoader` cannot be cast to `com.mongodb.client.MongoClient` by code compiled against a *different* copy of that class (or, in our case, code that doesn't reference it at compile time at all). So there is no way to get a usable typed reference the way `DriverShim` does for JDBC.

Instead, `MongoReflectionClient` drives every driver call through `java.lang.reflect.Method`:

```
com.example.mongo/
├── MongoExtLibManager.java     # Downloads bson/mongodb-driver-core/mongodb-driver-sync to extlib/, builds a shared URLClassLoader
├── MongoReflectionClient.java  # Class.forName(..., loader) + Method.invoke(...) for every driver call
└── MongoSessionManager.java    # Session storage: client + reflection wrapper as raw Objects
```

`MongoReflectionClient` covers: `connect(connectionString)` (`MongoClients.create`), `listDatabaseNames`, `getDatabase`, `listCollectionNames`, `getCollection`, `find(collection, filterJson, limit)` (parses the filter via `Document.parse`, converts results via `Document.toJson()`), and `close`.

One simplification is possible: `MongoIterable.iterator()` returns a `MongoCursor<T>`, which implements **`java.util.Iterator`** — a JDK-standard interface. So once a `MongoIterable` is obtained reflectively, its cursor *can* be cast directly to `Iterator<?>` and driven normally (`hasNext()`/`next()`) without further reflection — the same JDK-interface trick that makes `DriverShim` work, just for iteration rather than the whole client.

No Maven dependency on `org.mongodb:*` exists in `pom.xml` — the driver jars are pulled from Maven Central into `extlib/` on first connect, exactly like the JDBC drivers, just without a `DriverShim`-style typed wrapper.

### Mongo API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/data-browser/mongo/connect` | Connect via connection string; downloads/loads the driver on first use; returns `sessionId` + database names |
| `POST` | `/api/data-browser/mongo/collections` | List collection names in a database |
| `POST` | `/api/data-browser/mongo/find` | Run a find with an optional JSON filter (blank = match all), returns matching documents |
| `POST` | `/api/data-browser/mongo/disconnect` | Close the Mongo client and remove the session |

### Example: Connect

```bash
curl -X POST http://localhost:8080/api/data-browser/mongo/connect \
  -H "Content-Type: application/json" \
  -d '{"connectionString":"mongodb://user:pass@localhost:27017"}'
```

Response: `{"status":"success","data":{"sessionId":"<uuid>","status":"connected","databases":["mydb","admin"]}}`

### Example: Find

```bash
curl -X POST http://localhost:8080/api/data-browser/mongo/find \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<uuid>","database":"mydb","collection":"users","filter":"{\"active\":true}","limit":50}'
```

Response: `{"status":"success","data":{"documents":[{"_id":{"$oid":"..."},"name":"alice","active":true}]}}`

**Gotchas:**
- `MongoHandler.handleConnect` is not covered by automated tests — it calls the real `MongoExtLibManager` singleton, which downloads the driver jar over the network on first use. `MongoHandlerTest` covers `collections`/`find`/`disconnect` with a mocked `MongoReflectionClient` instead.
- Documents are rendered as one grid row each (no fixed column set, unlike SQL results) — `_id` plus pretty-printed JSON of the whole document.
- `find`'s JSON filter uses MongoDB's Extended JSON conventions (e.g. `{"_id": {"$oid": "..."}}`), since it's parsed with `Document.parse(...)`.

## Adding a New Data Source Type

This project now has two working patterns for connecting to something new (JDBC via `DataSourceStrategy`, and reflection-loaded via the Mongo mode), which is enough to generalize into a decision tree. Working through **AWS S3** as a worked example is useful precisely because it doesn't cleanly fit either existing pattern — S3 has no JDBC driver, but unlike MongoDB it also has no proprietary binary wire protocol requiring a vendor client at all.

### Step 1: Does it have a JDBC driver?

If yes, it's not a new pattern — follow [Adding a New Database](#adding-a-new-database): implement `DataSourceStrategy`, add one line to the services file, done. This covers most relational and relational-ish databases (Postgres, MySQL, Snowflake, and most others with a JDBC driver on Maven Central).

S3 has no JDBC driver (there are third-party SQL-over-S3 shims, but plain object storage isn't relational), so this doesn't apply.

### Step 2: Is the wire protocol accessible over plain HTTP?

This is the question that actually matters for S3, and the reason it's a *better* example than MongoDB rather than just "another one like it."

- **MongoDB speaks a proprietary binary protocol.** There is no way to talk to it without either the official driver or reimplementing that protocol by hand — not realistic. That forces the choice made in [MongoDB Browser Mode](#mongodb-browser-mode): dynamically load the real driver and drive it via reflection, or accept a compile-time dependency.
- **S3 is a REST API over plain HTTPS**, authenticated with a request-signing scheme (SigV4) built from HMAC-SHA256. Both `java.net.http.HttpClient` and `javax.crypto.Mac` are JDK-standard — nothing needs to be loaded dynamically or reflected into at all. A `S3Strategy`-equivalent could sign and send requests directly, with zero new dependencies of any kind (not even a `DriverShim`-style download).

So before reaching for the Mongo-style reflection wrapper, check whether the target's protocol is simple enough (REST/JSON/HTTP) to talk to directly with `HttpClient`. Reflection-loading is a fallback for genuinely proprietary client protocols, not a default.

### Step 3: If it does need a real client library, choose compile-time vs. reflection-loaded

Only relevant if step 2 says "no, it needs a real client" (true for Mongo, not true for S3):

| | Compile-time dependency | Reflection-loaded (Mongo model) |
|---|---|---|
| `pom.xml` | New `<dependency>` | None |
| Startup cost | Always on classpath | Downloaded to `extlib/` on first use |
| Code | Call the client API directly, fully typed | `Class.forName` + `Method.invoke` for every call |
| Matches existing JDBC UX | No — driver is bundled, not "downloaded on demand" | Yes |

This project's established convention (JDBC + Mongo) favors reflection-loaded, matching the "download on demand, no restart" UX users already see for JDBC drivers. Pick compile-time only when the library is small, stable, and the reflection overhead isn't worth it for the call surface involved.

### Worked sketch: adding S3 (not implemented — illustrative only)

Given S3 fits neither `DataSourceStrategy` (no JDBC) nor the Mongo pattern (no vendor client needed), it would be its own mode, mirroring Mongo's shape:

```
com.example.s3/
├── S3Client.java          # Signs + sends requests via java.net.http.HttpClient — no dependency, no reflection
└── S3SessionManager.java  # Per-session: access key, secret key, region, endpoint
```

- **Auth**: access key ID + secret access key (+ optional session token), not a connection string — closer to the JDBC user/password fields than to Mongo's single connection string.
- **"Databases" → buckets**: `GET /` (list buckets) replaces `listDatabaseNames`.
- **"Collections"/"Tables" → objects**: `GET /{bucket}?list-type=2&prefix=...` (list objects, optionally by prefix) replaces `listCollectionNames`/table listing.
- **"Query"/"Find" → object download or metadata**: `GET /{bucket}/{key}` for content, `HEAD /{bucket}/{key}` for metadata — there's no query language, so this replaces both `find` and `query` with a simpler get-by-key / list-by-prefix model.
- **UI**: a third Database Type entry ("AWS S3") swapping in access-key/secret-key/region fields, and yet another tab-label pair (e.g. "Bucket Browser" instead of "Collection Browser").

The one genuinely new problem S3 introduces that neither JDBC nor Mongo has: request signing. SigV4 needs the request method, canonical headers, and payload hash combined via HMAC-SHA256 — all doable with `javax.crypto.Mac` and `java.security.MessageDigest`, both JDK-standard, so still no new dependency.

## API Endpoints

All endpoints accept and return `application/json`. These are the JDBC-backed endpoints; see [Mongo API Endpoints](#mongo-api-endpoints) for the MongoDB mode.

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
| `MongoSessionManagerTest` | Session creation/lookup, `close()` called on removal (via mocked `MongoReflectionClient`) |
| `MongoHandlerTest` | `collections`, `find`, `disconnect` — success and session-expired paths, using a mocked `MongoReflectionClient` (`connect` is not covered; see class Javadoc) |
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
- **MongoDB mode** — selecting "MongoDB" in the Database Type dropdown swaps in a connection-string field, relabels the tabs to "Collection Browser" / "Find Query", and renders results as one JSON document per row instead of a SQL grid
