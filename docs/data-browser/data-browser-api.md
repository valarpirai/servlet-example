# Data Browser API

The JDBC-backed HTTP surface. Everything accepts and returns
`application/json`.

MongoDB has its own endpoints, listed in [MongoDB mode](./mongodb-mode.md).

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/data-browser/db-types` | Registered database types for the dropdown |
| `POST` | `/api/data-browser/driver-status` | Whether the driver jar is present |
| `POST` | `/api/data-browser/download` | Download and load a driver |
| `POST` | `/api/data-browser/connect` | Open a connection, returns `sessionId` |
| `POST` | `/api/data-browser/databases` | Databases visible on the server |
| `POST` | `/api/data-browser/select-database` | Switch the active database |
| `POST` | `/api/data-browser/tables` | Schemas and tables |
| `POST` | `/api/data-browser/query` | Run SQL, 100 rows per page |
| `POST` | `/api/data-browser/disconnect` | Close the connection |

Every response wraps its payload:

```json
{ "status": "success", "data": { } }
```

## Connect

```bash
curl -X POST http://localhost:8080/api/data-browser/connect \
  -H "Content-Type: application/json" \
  -d '{"dbType":"postgresql","url":"jdbc:postgresql://localhost:5432/mydb","user":"alice","password":"secret"}'
```

```json
{ "status": "success", "data": { "sessionId": "<uuid>", "status": "connected" } }
```

Every later call carries that `sessionId`.

## Driver status

```bash
curl -X POST http://localhost:8080/api/data-browser/driver-status \
  -H "Content-Type: application/json" \
  -d '{"dbType":"postgresql"}'
```

```json
{ "status": "success", "data": { "dbType": "postgresql", "downloaded": true } }
```

## List databases

```bash
curl -X POST http://localhost:8080/api/data-browser/databases \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<uuid>"}'
```

```json
{ "status": "success", "data": { "databases": ["appdb", "otherdb"], "current": "appdb" } }
```

## Switch database

```bash
curl -X POST http://localhost:8080/api/data-browser/select-database \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<uuid>","database":"otherdb"}'
```

```json
{ "status": "success", "data": { "database": "otherdb" } }
```

The mechanism differs per driver, see
[database switching](./database-switching.md).

## Query

```bash
curl -X POST http://localhost:8080/api/data-browser/query \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<uuid>","sql":"SELECT * FROM users","page":1}'
```

Results are paginated at 100 rows. Pass `page` to move through them.

## Expired sessions

A `sessionId` older than 30 minutes of inactivity is gone, and the endpoint
returns a session-expired error. The browser reconnects from credentials in
`localStorage`.

## Related

- [Data browser](./data-browser.md) — the feature and its sessions
- [Driver loading](./driver-loading.md) — driver-status and download
- [Database switching](./database-switching.md) — select-database behaviour
- [MongoDB mode](./mongodb-mode.md) — the Mongo endpoints
