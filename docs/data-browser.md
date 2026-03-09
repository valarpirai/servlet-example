# Data Browser

A web-based database browser at `/data-browser`. Connect to PostgreSQL, MySQL, or Snowflake — JDBC drivers are downloaded on demand, no server restart required.

## Quick Start

1. Start the server: `mvn -PappRun`
2. Open `http://localhost:8080/data-browser`
3. Select a database type, fill in connection details, click **Connect**
4. Browse tables or run SQL queries

## Supported Databases

| Type | JDBC URL format |
|---|---|
| PostgreSQL | `jdbc:postgresql://host:5432/dbname` |
| MySQL | `jdbc:mysql://host:3306/dbname` |
| Snowflake | Account-based fields (URL auto-built) |

Snowflake-specific fields: **Account**, Warehouse, Database, Schema, Role (optional fields shown dynamically in the UI).

## How Driver Loading Works

Drivers are downloaded from Maven Central to `extlib/` on first use and reused on subsequent connections. No restart needed — they are loaded into a `URLClassLoader` at runtime and registered with `DriverManager` via a `DriverShim`.

| Driver | JAR | Version |
|---|---|---|
| MySQL | `mysql-connector-j` | 8.4.0 |
| PostgreSQL | `postgresql` | 42.7.3 |
| Snowflake | `snowflake-jdbc` | 3.15.0 |

## API Endpoints

All endpoints accept and return `application/json`.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/data-browser/download` | Download + load JDBC driver |
| `POST` | `/api/data-browser/connect` | Open connection, returns `sessionId` |
| `POST` | `/api/data-browser/tables` | List schemas and tables |
| `POST` | `/api/data-browser/query` | Execute SQL, paginated (100 rows/page) |
| `POST` | `/api/data-browser/disconnect` | Close connection |

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

## Sessions

- Sessions live server-side, keyed by UUID `sessionId`
- TTL: **30 minutes** of inactivity, then auto-closed
- Credentials are stored in browser `localStorage` only — never persisted server-side
- On page reload, credentials are restored from `localStorage` and a new session is established

## UI Features

- **Table Browser tab** — expandable schema/table tree; click a table to query its rows
- **SQL Editor tab** — freeform SQL with `Cmd+Enter` / `Ctrl+Enter` shortcut to run
- **Results grid** — paginated (Prev / Next), handles nulls and binary columns (Base64)
- **Driver status badge** — shows whether the driver is already downloaded
