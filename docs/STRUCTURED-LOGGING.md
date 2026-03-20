# Structured Logging with Correlation IDs

## Overview

The application uses **structured logging** with **correlation IDs** for distributed tracing and better log analysis. Every HTTP request gets a unique correlation ID that persists through the entire request lifecycle.

## Features

- **Automatic correlation ID generation** for every request
- **Client-provided correlation IDs** supported via headers
- **MDC (Mapped Diagnostic Context)** for thread-local request context
- **Structured log formatting** for machine-readable logs
- **No external dependencies** - uses SLF4J/Logback built-in features

## Architecture

```
HTTP Request
    ↓
CorrelationIdFilter (generates or extracts correlation ID)
    ↓
MDC populated (correlationId, requestId, method, path, clientIp)
    ↓
Request Processing (all logs include MDC context automatically)
    ↓
Response (correlation ID added to headers)
    ↓
MDC cleared (prevents memory leaks)
```

## Components

### 1. CorrelationIdFilter

**Location**: `com.example.servlet.util.CorrelationIdFilter`

Servlet filter that:
- Generates or extracts correlation IDs
- Populates SLF4J MDC with request context
- Adds correlation ID to response headers
- Cleans up MDC after request completes

**MDC Fields Set:**
- `correlationId` - Unique ID for distributed tracing
- `requestId` - Short unique ID for this request
- `method` - HTTP method (GET, POST, etc.)
- `path` - Full request path with query string
- `clientIp` - Client IP (supports X-Forwarded-For, X-Real-IP)

### 2. StructuredLogger

**Location**: `com.example.servlet.util.StructuredLogger`

Wrapper around SLF4J logger that:
- Automatically includes MDC context in logs
- Supports custom structured fields
- Provides builder pattern for log construction
- Formats logs for readability and parsing

## Usage

### Basic Logging with Automatic Context

```java
import com.example.servlet.util.StructuredLogger;
import org.slf4j.LoggerFactory;

private static final StructuredLogger logger =
    StructuredLogger.create(LoggerFactory.getLogger(MyClass.class));

// Simple log - automatically includes correlationId, requestId, etc.
logger.info("Processing request");

// Output: "Processing request | correlationId=abc-123 requestId=xyz-789 method=GET path=/api/test"
```

### Logging with Custom Fields

```java
import java.util.HashMap;
import java.util.Map;

Map<String, Object> fields = new HashMap<>();
fields.put("userId", 12345);
fields.put("action", "upload");
fields.put("fileSize", 1024000);

logger.info("File uploaded successfully", fields);

// Output: "File uploaded successfully | correlationId=abc-123 requestId=xyz-789 userId=12345 action=upload fileSize=1024000"
```

### Using Builder Pattern

```java
logger.with("Processing payment")
    .field("userId", 12345)
    .field("amount", 99.99)
    .field("currency", "USD")
    .info();

// Output: "Processing payment | correlationId=abc-123 userId=12345 amount=99.99 currency=USD"
```

### Error Logging with Exception

```java
try {
    // ... some operation
} catch (Exception e) {
    logger.with("Failed to process request")
        .field("userId", 12345)
        .field("retryCount", 3)
        .error(e);
}
```

## Client Usage

### Providing Correlation ID

Clients can provide their own correlation ID to trace requests across services:

```bash
curl -H "X-Correlation-ID: my-trace-id-123" \
     http://localhost:8080/api/test
```

The server will use the provided correlation ID and return it in the response:

```
HTTP/1.1 200 OK
X-Correlation-ID: my-trace-id-123
X-Request-ID: xyz-789
...
```

### No Correlation ID Provided

If no correlation ID is provided, the server generates one:

```bash
curl http://localhost:8080/api/test
```

Response:

```
HTTP/1.1 200 OK
X-Correlation-ID: 550e8400-e29b-41d4-a716-446655440000
X-Request-ID: abc-123
...
```

## Log Format

### Log Entry Structure

```
<message> | <field1>=<value1> <field2>=<value2> ...
```

### Example Logs

**Successful request:**
```
Request completed | correlationId=550e8400-e29b-41d4-a716-446655440000 requestId=abc-123 method=GET path=/api/test statusCode=200 responseTimeMs=15 responseSize=0 requestCount=42
```

**Error:**
```
Request failed with exception | correlationId=550e8400-e29b-41d4-a716-446655440000 requestId=def-456 method=POST path=/api/upload responseTimeMs=250 exceptionType=java.io.IOException uri=/api/upload
```

## Configuration

No special configuration needed! The filter is automatically registered in `Main.java`:

```java
// Add CorrelationIdFilter for structured logging
FilterDef filterDef = new FilterDef();
filterDef.setFilterName("CorrelationIdFilter");
filterDef.setFilter(new CorrelationIdFilter());
context.addFilterDef(filterDef);

FilterMap filterMap = new FilterMap();
filterMap.setFilterName("CorrelationIdFilter");
filterMap.addURLPattern("/*");
context.addFilterMap(filterMap);
```

## Distributed Tracing

### Scenario: Multi-Service Request

```
Client → Service A → Service B → Service C
```

**Flow:**

1. Client sends request to Service A:
   ```
   GET /api/process
   ```

2. Service A generates correlation ID and calls Service B:
   ```
   GET /api/validate
   X-Correlation-ID: 550e8400-e29b-41d4-a716-446655440000
   ```

3. Service B forwards correlation ID to Service C:
   ```
   GET /api/lookup
   X-Correlation-ID: 550e8400-e29b-41d4-a716-446655440000
   ```

4. All services log with the same correlation ID:
   ```
   Service A: Request completed | correlationId=550e8400-e29b-41d4-a716-446655440000 ...
   Service B: Request completed | correlationId=550e8400-e29b-41d4-a716-446655440000 ...
   Service C: Request completed | correlationId=550e8400-e29b-41d4-a716-446655440000 ...
   ```

5. Search logs by correlation ID to trace the entire flow:
   ```bash
   grep "550e8400-e29b-41d4-a716-446655440000" logs/application.log
   ```

## Log Aggregation

### Parsing Logs

The structured format is easy to parse with standard tools:

**Using awk to extract fields:**
```bash
# Extract all correlation IDs
grep "correlationId=" logs/application.log | awk -F'correlationId=' '{print $2}' | awk '{print $1}'

# Extract response times
grep "responseTimeMs=" logs/application.log | awk -F'responseTimeMs=' '{print $2}' | awk '{print $1}'
```

**Using jq for JSON conversion:**

If you need JSON format, you can transform logs:

```bash
# Convert to JSON (requires additional processing)
grep "correlationId=" logs/application.log | \
  sed 's/| /\n/g' | \
  jq -Rs 'split("\n") | map(select(length > 0)) | map(split("=")) | map({(.[0]): .[1]}) | add'
```

### Log Analysis

**Find slow requests (>1000ms):**
```bash
grep "responseTimeMs=" logs/application.log | \
  awk -F'responseTimeMs=' '{print $2}' | \
  awk '{if ($1 > 1000) print}' | \
  wc -l
```

**Count requests by path:**
```bash
grep "path=" logs/application.log | \
  awk -F'path=' '{print $2}' | \
  awk '{print $1}' | \
  sort | uniq -c | sort -nr
```

**Find all errors for a specific correlation ID:**
```bash
grep "550e8400-e29b-41d4-a716-446655440000" logs/application.log | grep ERROR
```

## Best Practices

### 1. Always Use StructuredLogger for Request Processing

```java
// ✅ Good - includes correlation ID
StructuredLogger logger = StructuredLogger.create(LoggerFactory.getLogger(MyClass.class));
logger.info("Processing request");

// ❌ Bad - no correlation ID
Logger logger = LoggerFactory.getLogger(MyClass.class);
logger.info("Processing request");
```

### 2. Add Relevant Context to Logs

```java
// ✅ Good - includes important context
logger.with("User login")
    .field("userId", userId)
    .field("ipAddress", ipAddress)
    .field("loginMethod", "oauth")
    .info();

// ❌ Bad - missing context
logger.info("User login");
```

### 3. Forward Correlation ID in Outbound Requests

```java
// When calling external services, forward the correlation ID
String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/data"))
    .header("X-Correlation-ID", correlationId)
    .build();
```

### 4. Don't Log Sensitive Data

```java
// ❌ Bad - logging sensitive data
logger.with("Payment processed")
    .field("creditCardNumber", cardNumber)  // DON'T DO THIS
    .field("password", password)            // DON'T DO THIS
    .info();

// ✅ Good - log only non-sensitive identifiers
logger.with("Payment processed")
    .field("paymentId", paymentId)
    .field("last4Digits", lastFour)
    .info();
```

## Testing

### Testing with Correlation IDs

```java
import org.slf4j.MDC;

@Test
public void testRequestWithCorrelationId() {
    // Set up MDC for testing
    MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "test-correlation-id");
    MDC.put(CorrelationIdFilter.REQUEST_ID_MDC_KEY, "test-request-id");

    try {
        // Your test code here
        // All logs will include the test correlation ID
    } finally {
        MDC.clear(); // Always clean up
    }
}
```

### Testing CorrelationIdFilter

See `CorrelationIdFilterTest.java` for examples:
- Testing correlation ID generation
- Testing MDC population
- Testing MDC cleanup
- Testing IP extraction from proxy headers

## Migration Guide

### From Standard Logger

**Before:**
```java
private static final Logger logger = LoggerFactory.getLogger(MyClass.class);

logger.info("Processing request for user: " + userId);
```

**After:**
```java
private static final StructuredLogger logger =
    StructuredLogger.create(LoggerFactory.getLogger(MyClass.class));

logger.with("Processing request")
    .field("userId", userId)
    .info();
```

### Benefits of Migration

1. **Automatic correlation IDs** - No manual tracking needed
2. **Structured fields** - Easy to parse and analyze
3. **Consistent format** - All logs follow the same structure
4. **Better searchability** - Find logs by any field
5. **Distributed tracing** - Track requests across services

## Troubleshooting

### MDC Not Appearing in Logs

**Problem:** Correlation ID and other MDC fields not showing in logs.

**Solution:** Ensure `CorrelationIdFilter` is registered before all servlets in `Main.java`.

### Memory Leaks

**Problem:** MDC values persist across requests.

**Solution:** `CorrelationIdFilter` automatically clears MDC in `finally` block. If using MDC manually, always call `MDC.clear()` in finally block.

### Correlation ID Not Forwarded

**Problem:** Correlation ID changes between services.

**Solution:** Always extract and forward the `X-Correlation-ID` header when making outbound HTTP requests.

## Performance Impact

- **Minimal overhead**: ~1-2ms per request
- **Memory**: ~1KB per request (MDC context)
- **Thread-safe**: Uses ThreadLocal for isolation
- **No blocking**: All operations are non-blocking

## Future Enhancements

- [ ] JSON log format option
- [ ] Integration with distributed tracing systems (Jaeger, Zipkin)
- [ ] Async logging support
- [ ] Log sampling for high-volume endpoints
- [ ] Metrics based on correlation IDs

## References

- **SLF4J MDC**: http://www.slf4j.org/manual.html#mdc
- **Distributed Tracing**: https://opentelemetry.io/docs/concepts/signals/traces/
- **Correlation IDs**: https://blog.rapid7.com/2016/12/23/the-value-of-correlation-ids/
