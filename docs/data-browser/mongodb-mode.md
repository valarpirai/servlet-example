# MongoDB Mode

MongoDB runs on a separate path from the JDBC strategies.

There is no JDBC driver, no `Connection`, no `ResultSet` and no tabular schema.
Rather than force that shape, MongoDB gets its own package, session manager,
handler and UI mode. It is not a `DataSourceStrategy` and does not appear in
`/api/data-browser/db-types`.

```
com.example.mongo/
├── MongoExtLibManager.java     Downloads driver jars, builds a shared URLClassLoader
├── MongoReflectionClient.java  Class.forName plus Method.invoke for every call
└── MongoSessionManager.java    Session storage, client held as a raw Object
```

## Why reflection instead of a shim

The shim trick for JDBC depends on `java.sql.Driver` being a JDK type, explained
in [driver loading](./driver-loading.md).

MongoDB has no JDK equivalent. `com.mongodb.client.MongoClient` is a vendor
type, so nothing can be cast to it by code that never referenced it at compile
time. No typed reference is obtainable, so `MongoReflectionClient` drives every
call through `java.lang.reflect.Method`.
The project carries no `org.mongodb` Maven dependency at all. Driver jars —
`bson`, `mongodb-driver-core`, `mongodb-driver-sync`, version 5.2.1 — are
downloaded into `data/extlib/` on first connect, in dependency order.

## What the client covers

`connect` via `MongoClients.create`, `listDatabaseNames`, `getDatabase`,
`listCollectionNames`, `getCollection`, `find` and `close`.

`find` parses its filter with `Document.parse` and converts results with
`Document.toJson`.

One shortcut is worth knowing. `MongoIterable.iterator()` returns a
`MongoCursor`, which implements `java.util.Iterator`. So once an iterable is
obtained reflectively, its cursor can be cast and driven with plain `hasNext()`
and `next()`, shrinking the reflective surface.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/data-browser/mongo/connect` | Connect by connection string, returns `sessionId` and database names |
| `POST` | `/api/data-browser/mongo/collections` | List collections in a database |
| `POST` | `/api/data-browser/mongo/find` | Find with an optional JSON filter, blank matches all |
| `POST` | `/api/data-browser/mongo/disconnect` | Close the client, drop the session |

```bash
curl -X POST http://localhost:8080/api/data-browser/mongo/connect \
  -H "Content-Type: application/json" \
  -d '{"connectionString":"mongodb://user:pass@localhost:27017"}'
```

```bash
curl -X POST http://localhost:8080/api/data-browser/mongo/find \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<uuid>","database":"mydb","collection":"users","filter":"{\"active\":true}","limit":50}'
```

## Things to know

Filters use MongoDB Extended JSON, because `Document.parse` handles them. An id
lookup is `{"_id": {"$oid": "..."}}`.

Results render one document per row, `_id` plus the pretty-printed document.
There is no fixed column set, unlike a SQL result.

`MongoHandler.handleConnect` has no automated test. It calls the real
`MongoExtLibManager`, which downloads jars over the network. `MongoHandlerTest`
covers `collections`, `find` and `disconnect` against a mocked
`MongoReflectionClient`.

## Related

- [Driver loading](./driver-loading.md) — the shim approach this cannot use
- [Choosing an integration](./choosing-an-integration.md) — when reflection is right
- [Data browser](./data-browser.md) — the UI mode switch
