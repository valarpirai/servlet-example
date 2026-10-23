# Log Format

Every line is a message, a pipe, then `key=value` pairs.

```
<message> | <field>=<value> <field>=<value> ...
```

This stays readable by eye and parses with `awk`. No JSON encoder is involved.

## Examples

A completed request:

```
Request completed | correlationId=550e8400-e29b-41d4-a716-446655440000 requestId=abc-123 method=GET path=/api/test statusCode=200 responseTimeMs=15 responseSize=0 requestCount=42
```

A failure:

```
Request failed with exception | correlationId=550e8400-e29b-41d4-a716-446655440000 requestId=def-456 method=POST path=/api/upload responseTimeMs=250 exceptionType=java.io.IOException uri=/api/upload
```

`RouterServlet` emits both. `responseTimeMs` and `statusCode` are always
present on completion, which is what makes latency queries possible without a
metrics backend.

## Searching

Follow the log:

```bash
tail -f logs/application.log
```

Trace one request or one operation:

```bash
grep "correlationId=abc-123" logs/application.log
```

Find slow requests:

```bash
grep "responseTimeMs=" logs/application.log |
  awk -F'responseTimeMs=' '{print $2}' |
  awk '{print $1}' |
  sort -n | tail -10
```

Count by status code:

```bash
grep -o 'statusCode=[0-9]*' logs/application.log | sort | uniq -c | sort -rn
```

## Configuration

Appenders and third-party log levels live in `logback.xml`. Everything else is a
seeded property.

| Property | Default |
|---|---|
| `logging.level` | `INFO` |
| `logging.fileEnabled` | `true` |
| `logging.filePath` | `logs/application.log` |
| `logging.fileMaxSize` | `10MB` |
| `logging.fileMaxHistory` | `30` |
| `logging.fileTotalSizeCap` | `1GB` |
| `logging.consolePattern` | timestamp, thread, level, logger, message |
| `logging.filePattern` | same as console |

Files roll at 10 MB, keep 30 archives, and stop at 1 GB total.

`logback.xml` pins `org.apache.catalina`, `org.apache.coyote` and
`org.apache.tomcat` to `WARN`. Tomcat is chatty at `INFO` and would bury
application lines.

## Bootstrap logging is different

`LoggingConfig` configures logging before properties are readable, using
hardcoded defaults. Only the earliest startup lines use those. See
[startup flow](../architecture/startup-flow.md).

## The logs directory is not tracked

`logs/` is gitignored, like `data/attachments/`.

## Related

- [Structured logging](./structured-logging.md) — producing these lines
- [Correlation IDs](./correlation-ids.md) — the fields that enable tracing
- [Configuration](../architecture/configuration.md) — all logging properties
