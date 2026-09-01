# Correlation IDs

One ID follows a request through every log line it produces.

`CorrelationIdFilter` runs on `/*`, registered in `Main` at startup. It reads or
generates an ID, populates the MDC, adds response headers, and clears the MDC
when the request finishes.

```
HTTP Request
    ↓
CorrelationIdFilter          generate or extract the ID
    ↓
MDC populated                correlationId, requestId, method, path, clientIp
    ↓
Request processing           every log line picks up the context
    ↓
Response headers             X-Correlation-ID, X-Request-ID
    ↓
MDC cleared                  thread reuse must not leak context
```

Clearing matters. Tomcat pools threads, so an uncleared MDC would attach one
request's context to the next request on that thread.

## MDC fields

| Field | Meaning |
|---|---|
| `correlationId` | Traces one logical operation, possibly across services |
| `requestId` | Identifies this single HTTP request |
| `method` | HTTP method |
| `path` | Path, including query string |
| `clientIp` | Client address, honouring `X-Forwarded-For` and `X-Real-IP` |

`correlationId` and `requestId` differ on purpose. A correlation ID can span
services and retries. A request ID never does.

## Clients can supply one

Send `X-Correlation-ID` and the server adopts it.

```bash
curl -H "X-Correlation-ID: my-trace-id-123" http://localhost:8080/api/test
```

```
HTTP/1.1 200 OK
X-Correlation-ID: my-trace-id-123
X-Request-ID: xyz-789
```

Send nothing and the server generates a UUID. Either way both headers come back,
so a client can always record what to search for later.

## Tracing across services

Forward the header on outbound calls. Every service logs the same
`correlationId`, so one grep reconstructs the whole path.

```
Client → Service A → Service B → Service C
         all logging correlationId=550e8400-...
```

```bash
grep "550e8400-e29b-41d4-a716-446655440000" logs/application.log
```

A service that drops the header breaks the chain from that point on. Nothing
errors; the trace just ends.

## Related

- [Structured logging](./structured-logging.md) — reading the context in code
- [Log format](./log-format.md) — searching and extracting
- [Request flow](../architecture/request-flow.md) — filter position
