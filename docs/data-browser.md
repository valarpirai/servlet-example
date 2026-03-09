# Data Browser

A web-based database browser at `/data-browser`. Connect to PostgreSQL, MySQL, or Snowflake — JDBC drivers are downloaded on demand, no server restart required.

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

All per-database behaviour is encapsulated in a `DataSourceStrategy` implementation. The `DataSourceRegistry` singleton holds all registered strategies.

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
- `getJarPath()` — Maven Central path for the JDBC JAR
- `getDriverClass()` — fully-qualified driver class name
- `buildUrl(Map<String,String> props)` — assembles JDBC URL from user input
- `buildConnectionProperties(user, password, props)` — builds `java.util.Properties` (adds Snowflake extras: warehouse, db, schema, role)
- `getSystemSchemas()` — schemas hidden from the table browser

### Adding a New Database

1. Create `src/main/java/com/example/datasource/YourDbStrategy.java` implementing `DataSourceStrategy`
2. Register it in `DataSourceRegistry` constructor: `register(new YourDbStrategy())`
3. Add connection fields to `DB_FIELDS` in `data-browser.html`

No changes needed in `ExtLibManager` or `DataBrowserHandler`.

### How Driver Loading Works

Drivers are downloaded from Maven Central to `extlib/` on first use and reused on subsequent connections. No restart needed — they are loaded into a `URLClassLoader` at runtime and registered with `DriverManager` via a `DriverShim`.

| Driver | JAR | Version |
|---|---|---|
| PostgreSQL | `postgresql` | 42.7.3 |
| MySQL | `mysql-connector-j` | 8.4.0 |
| Snowflake | `snowflake-jdbc` | 3.15.0 |

## API Endpoints

All endpoints accept and return `application/json`.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/data-browser/driver-status` | Check if driver JAR is downloaded |
| `POST` | `/api/data-browser/download` | Download + load JDBC driver |
| `POST` | `/api/data-browser/connect` | Open connection, returns `sessionId` |
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

### Example: Query

```bash
curl -X POST http://localhost:8080/api/data-browser/query \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<uuid>","sql":"SELECT * FROM users","page":1}'
```

## Testing

Unit tests cover the datasource strategy layer and core utilities (51 tests, JUnit 5 + Mockito):

| Test Class | Coverage |
|---|---|
| `DataSourceRegistryTest` | Singleton lookup, case-insensitive get, unknown types |
| `PostgreSqlStrategyTest` | URL pass-through, validation, system schemas |
| `MySqlStrategyTest` | URL pass-through, validation, system schemas |
| `SnowflakeStrategyTest` | URL assembly, optional fields, blank-field skipping |
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
