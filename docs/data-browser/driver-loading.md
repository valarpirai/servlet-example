# Driver Loading

JDBC drivers are not bundled. They are fetched at runtime.

On first connection, `ExtLibManager` downloads the jar named by
`getJarPath()` from Maven Central into `data/extlib/`, loads it into a
`URLClassLoader`, and registers it with `DriverManager` through a `DriverShim`.
No restart is needed. Later connections reuse the cached jar.

## Current drivers

| Database | Artifact | Version |
|---|---|---|
| PostgreSQL | `postgresql` | 42.7.3 |
| MySQL | `mysql-connector-j` | 8.4.0 |
| Snowflake | `snowflake-jdbc` | 3.15.0 |

These come from each strategy's `getJarPath`. Changing a version means editing
that method.

## Why DriverShim is needed

`DriverManager` refuses to use a driver loaded by a classloader it does not
trust. The shim is a thin `java.sql.Driver` implementation, loaded by the
application classloader, that delegates every call to the real driver instance
from the `URLClassLoader`.

`DriverManager` sees a class it trusts. The work happens in the downloaded one.

## Why this works at all

`java.sql.Driver`, `Connection` and `ResultSet` are JDK types, loaded once by the
platform classloader. A class loaded from any classloader that implements
`java.sql.Driver` can be cast to `Driver` from anywhere.

That shared-interface property is the whole trick. It is also exactly what
MongoDB lacks, which is why it needs a different approach — see
[MongoDB mode](./mongodb-mode.md).

## Checking status

`POST /api/data-browser/driver-status` reports whether a jar is already present.
The UI badge uses it.

```bash
curl -X POST http://localhost:8080/api/data-browser/driver-status \
  -H "Content-Type: application/json" \
  -d '{"dbType":"postgresql"}'
```

`POST /api/data-browser/download` fetches and loads it.

## Driver jars are not tracked

`data/extlib/*.jar` is gitignored. Jars are a runtime cache, and a fresh clone
downloads what it needs on first connect.

## Related

- [DataSource strategies](./datasource-strategies.md) — where jar paths come from
- [MongoDB mode](./mongodb-mode.md) — loading a driver with no shimmable interface
- [API endpoints](./data-browser-api.md) — driver-status and download
