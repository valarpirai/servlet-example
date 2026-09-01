# Structured Logging

Every log line carries request context without the caller passing it.

`StructuredLogger` wraps an SLF4J logger and merges the current MDC into each
message. `CorrelationIdFilter` fills that MDC once per request. The result is
that any log statement, anywhere in the call stack, knows which request it
belongs to.

No extra dependency. This is SLF4J and Logback doing what they already do.

## Using it

```java
import com.example.util.StructuredLogger;
import org.slf4j.LoggerFactory;

private static final StructuredLogger logger =
    StructuredLogger.create(LoggerFactory.getLogger(MyClass.class));

logger.info("Processing request");
```

```
Processing request | correlationId=abc-123 requestId=xyz-789 method=GET path=/api/test
```

The context comes free. Nothing was passed in.

## Adding fields

Pass a map for one-off structured data.

```java
Map<String, Object> fields = new HashMap<>();
fields.put("userId", 12345);
fields.put("action", "upload");

logger.info("File uploaded", fields);
```

Or build it fluently, which reads better with more than two fields.

```java
logger.with("Processing payment")
    .field("userId", 12345)
    .field("amount", 99.99)
    .field("currency", "USD")
    .info();
```

## Errors

Pass the exception to `error`. The type and message join the structured fields.

```java
try {
  process();
} catch (Exception e) {
  logger.with("Failed to process request")
      .field("userId", 12345)
      .field("retryCount", 3)
      .error(e);
}
```

## Already wired

`RouterServlet`, `AttachmentHandler` and `DataBrowserHandler` log through this
already. Request start, completion, status and duration come from
`RouterServlet` without any per-handler code.

## Adding it to a new class

One line. There is no registration and no configuration.

```java
private static final StructuredLogger logger =
    StructuredLogger.create(LoggerFactory.getLogger(MyClass.class));
```

## Related

- [Correlation IDs](./correlation-ids.md) — where the context comes from
- [Log format](./log-format.md) — the line format and searching it
- [Request flow](../architecture/request-flow.md) — where the filter sits
