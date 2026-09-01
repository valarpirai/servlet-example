# Runtime Issues

## Port already in use

```bash
lsof -i :8080
kill <pid>

# or move
SERVER_PORT=9090 mvn -PappRun
```

## OutOfMemoryError

This should be rare. Chunked streaming bounds heap by concurrency, not file
size.

Check, in order:

1. Is something bypassing chunked streaming?
2. Is anything calling `Files.readAllBytes` or sizing a `byte[]` from file
   length?
3. How many requests are in flight?

```bash
curl localhost:8080/metrics
```

If heap tracks file size rather than request count, some path loads a whole
file. See [memory guarantee](../storage/memory-guarantee.md).

```bash
java -XX:+HeapDumpOnOutOfMemoryError -jar target/servlet-example.jar
```

## App starts in YAML-only mode

**Symptom.** A startup warning about database properties, and seeded property
values are not applied.

**Cause.** `db.url`, `db.username` or `db.password` is missing or the database is
unreachable.

The app degrades on purpose and keeps serving. Property lookups fall through to
`application.yml`, which holds only the `db` block, so everything else uses its
in-code default. Those defaults do not always match the seeded values —
`script.maxMemory` is 10 MB in code and 50 MB seeded.

See [configuration](../architecture/configuration.md).

## Migration failed at startup

Flyway logs the error and the app falls back to YAML defaults. It still starts.

Do not edit an applied migration to fix it. Flyway validates checksums of
applied migrations, so editing one breaks startup on every existing database.
Add a new `V{n}__` file instead.

## Attachments disappeared after running tests

`StorageTestHelper.cleanupAttachmentsDirectory` deletes the storage directory
recursively so each test class starts clean. That directory is
`data/attachments`, the same path the app uses by default.

Running the suite wipes locally stored attachments. This is test design, not a
bug, but it will surprise you once.

## Driver jar missing after a fresh clone

`data/extlib/*.jar` is gitignored. Drivers download on first connect. See
[driver loading](../data-browser/driver-loading.md).

## Related

- [Debugging](../development/debugging.md) — logs, debugger, profiling
- [Startup flow](../architecture/startup-flow.md) — the degradation path
- [Database issues](./database-issues.md) — connection and driver problems
