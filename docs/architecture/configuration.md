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

## Precedence

`PropertiesUtil` answers every lookup in this order:

1. LRU cache
2. `app_properties` table
3. `application.yml`

Without a reachable database the app logs a warning, skips migrations, and
serves in-code defaults. Those are not always the seeded values:
`script.maxMemory` is 10 MB in code and 50 MB seeded. Attachment storage falls
back to the filesystem, because `storage.type` cannot be read.

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

`db/V2__seed_app_properties.sql` seeds defaults. Change a value with the
properties REST API or with SQL. The seed uses `ON CONFLICT DO NOTHING`, so
edits survive a restart.

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `application.environment` | `dev` | `dev` or `prod` |
| `upload.maxFileSize` | `10485760` | Max upload, 10 MB |
| `upload.maxRequestSize` | `52428800` | Max multipart request, 50 MB |
| `upload.fileSizeThreshold` | `1048576` | Spill to disk above 1 MB |
| `threadPool.maxThreads` | `200` | Tomcat threads |
| `threadPool.acceptCount` | `100` | Accept queue |
| `script.timeout` | `5000` | Script timeout, ms |
| `script.maxMemory` | `52428800` | Heap growth per script, 50 MB |
| `script.optimizationLevel` | `-1` | Rhino interpreted mode |
| `script.instructionThreshold` | `10000` | Instructions between limit checks |
| `modules.directory` | `data/modules` | Module storage root |
| `modules.maxFileSize` | `1048576` | Max module file, 1 MB |
| `storage.type` | `database` | `database` or `filesystem` |
| `storage.chunkSize` | `1048576` | Streaming chunk, 1 MB |
| `storage.filesystem.path` | `data/attachments` | Filesystem storage root |
| `storage.database.table` | `attachment_chunks` | Chunk table |
| `logging.level` | `INFO` | Root log level |
| `logging.filePath` | `logs/application.log` | Log file |

The default storage backend is `database`, not the filesystem.

## Resource layout

```
src/main/resources/
├── application.yml          Database credentials
├── routes.yml               All HTTP routes
├── logback.xml              Appenders and third-party log levels
├── db/                      Flyway migrations, V1 to V3
├── META-INF/services/       DataSourceStrategy registrations
├── templates/               HTML templates
└── static/                  index.html, script-editor.html, data-browser.html
```

## Migrations

Flyway runs pending migrations from `classpath:db` at startup. Files follow
`V{n}__{description}.sql`. Applied versions are skipped.

A failed migration logs an error and falls back to YAML. The server still
starts, without database-backed properties.

## Related

- [Startup flow](./startup-flow.md) — when each source loads
- [Route configuration](../routing/route-configuration.md) — the routes.yml format
- [Storage backends](../storage/storage-backends.md) — choosing database or filesystem
- [Log format](../observability/log-format.md) — logging properties
