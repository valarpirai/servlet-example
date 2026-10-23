# Configuration

Where settings live and which source wins.

`application.yml` holds database credentials and nothing else. Every other
property lives in the `app_properties` table, seeded by a Flyway migration.

## application.yml

```yaml
# Bootstrap only - just enough to reach the database
db:
  url:      ${DB_URL:jdbc:postgresql://localhost:5432/appdb}
  username: ${DB_USERNAME:postgres}
  password: ${DB_PASSWORD:postgres}
```

This is deliberate. Properties that live in a table can change without a
redeploy. Credentials cannot come from the database that needs them, so they
stay in the file.

## Connection pool

`DbConfig` holds one HikariCP pool for the whole JVM. `Database` borrows from it
and returns the connection on close. The pool holds up to 10 connections.
Set `db.pool.size` in `application.yml` to change that. The table cannot hold
it, because the pool must exist before the table can be read.

## Precedence

`PropertiesUtil` answers every lookup in this order:

1. LRU cache
2. `app_properties` table
3. `application.yml`

The app needs the database. When it is unreachable, or the credentials are
missing, startup stops with exit code 1 and a message naming the cause.

Environment variables are not a general override. Only two paths exist:

- A `${VAR:default}` placeholder in `application.yml`. `PropertiesUtil`
  resolves these against the environment, then system properties, then the
  inline default. This is how `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` work.
- `server.port`, which `Main` reads specially: `-Dserver.port`, then
  `SERVER_PORT`, then `app_properties`, then 8080.

No other property responds to an environment variable. A property in
`app_properties` holds a literal value with no placeholder, so nothing resolves
it.

## Seeded properties

`db/V2__seed_app_properties.sql` seeds defaults. Change a value with SQL or with
`TableRecord`. The seed uses `ON CONFLICT DO NOTHING`, so edits survive a restart.

A cached value lives up to 5 seconds. After an edit the new value appears within
that time, on every node. No reload is needed.

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `application.environment` | `dev` | `dev` or `prod` |
| `upload.maxFileSize` | `10485760` | Max upload, 10 MB |
| `upload.maxRequestSize` | `52428800` | Max multipart request, 50 MB |
| `upload.fileSizeThreshold` | `1048576` | Spill to disk above 1 MB |
| `threadPool.maxThreads` | `200` | Tomcat threads |
| `threadPool.minSpareThreads` | `10` | Idle Tomcat threads kept ready |
| `threadPool.acceptCount` | `100` | Accept queue |
| `threadPool.connectionTimeout` | `20000` | Connection timeout, ms |
| `script.timeout` | `5000` | Script timeout, ms |
| `script.maxMemory` | `52428800` | Heap growth per script, 50 MB |
| `script.optimizationLevel` | `-1` | Rhino interpreted mode |
| `script.instructionThreshold` | `10000` | Instructions between limit checks |
| `modules.maxFileSize` | `1048576` | Max module file, 1 MB |
| `storage.type` | `database` | `database` or `filesystem` |
| `storage.chunkSize` | `1048576` | Streaming chunk, 1 MB |
| `storage.filesystem.path` | `data/attachments` | Filesystem storage root |
| `storage.database.table` | `attachment_chunks` | Chunk table |
| `record.allowedTables` | `js_modules:name,routes:id,app_properties:id,business_rules:id` | Tables `TableRecord` may touch, as `table:keyColumn` |
| `record.maxRuleDepth` | `5` | Deepest nesting of rules that fire rules |
| `logging.level` | `INFO` | Root log level |
| `logging.filePath` | `logs/application.log` | Log file |

The default storage backend is `database`, not the filesystem.

## Resource layout

```
src/main/resources/
├── application.yml          Database credentials
├── logback.xml              Appenders and third-party log levels
├── db/                      Flyway migrations
├── META-INF/services/       DataSourceStrategy registrations
├── templates/               HTML templates
└── static/                  index.html, script-editor.html, data-browser.html
```

## Migrations

Flyway runs pending migrations from `classpath:db` at startup. Files follow
`V{n}__{description}.sql`. Applied versions are skipped.

A failed migration stops startup. See [startup flow](./startup-flow.md).

## Related

- [Startup flow](./startup-flow.md) — when each source loads
- [Route configuration](../routing/route-configuration.md) — the route fields
- [Storage backends](../storage/storage-backends.md) — choosing database or filesystem
- [Log format](../observability/log-format.md) — logging properties
