# Debugging

Logs first, debugger second, profiler last.

## Raise the log level

`logging.level` is a seeded property, so change it in `app_properties` rather
than in a file. `logback.xml` controls appenders and third-party levels.

```bash
tail -f logs/application.log
grep "correlationId=abc-123" logs/application.log
```

Every request line carries a correlation ID, so tracing one request end to end
is a single grep. See [correlation IDs](../observability/correlation-ids.md).

## Find slow requests

```bash
grep "responseTimeMs=" logs/application.log |
  awk -F'responseTimeMs=' '{print $2}' |
  awk '{print $1}' |
  sort -n | tail -10
```

## Attach a debugger

```bash
mvn -PappRun -Dexec.args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

Then connect the IDE to `localhost:5005`.

Useful breakpoints for a route that will not match: `RouteRegistry.findRoute`
and `RouteDispatcher.dispatch`.

## Check runtime state

```bash
curl localhost:8080/health
curl localhost:8080/metrics
```

`/metrics` reports heap use, uptime and request count. It is the fastest way to
confirm a memory suspicion — heap that tracks file size means something loads a
whole file.

## Memory

```bash
java -Xms512m -Xmx1024m -jar target/servlet-example.jar
java -XX:+HeapDumpOnOutOfMemoryError -jar target/servlet-example.jar
jcmd <pid> GC.heap_info
```

A heap dump on OOM is worth enabling whenever you are chasing a leak. Analyse it
with any heap analyser.

## Profiling

```bash
java -XX:StartFlightRecording=duration=60s,filename=recording.jfr \
  -jar target/servlet-example.jar
```

Open the recording in JDK Mission Control.

## Reflection failures

Handler and processor names resolve through string literals, so a rename can
break dispatch while compiling cleanly. A `ClassNotFoundException` or
"Handler invocation failed" points at
[route registry](../routing/route-registry.md) registration rather than at the
handler itself.

## Related

- [Log format](../observability/log-format.md) — fields and queries
- [Routing issues](../troubleshooting/routing-issues.md) — 404s and dispatch errors
- [Runtime issues](../troubleshooting/runtime-issues.md) — ports, memory, startup
