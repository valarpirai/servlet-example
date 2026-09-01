# JSON and Serialization

## Failed writing Instant

**Symptom.** A `JsonIOException` when serializing an object that has a
`java.time.Instant` field.

**Cause.** A bare `new Gson()` instead of `JsonUtil`.

```java
// Wrong
Gson gson = new Gson();
String json = gson.toJson(attachment);

// Right
String json = JsonUtil.toJson(attachment);
```

`JsonUtil` registers a custom `InstantTypeAdapter`. A plain Gson instance has no
idea what to do with `Instant`, so it either throws or writes an unusable
object.

**Where this bites.** Attachment metadata carries `Instant` fields, so
`LocalFileSystemStorage` metadata read and write must go through `JsonUtil`.
Anything persisting a model from `com.example.model` is in the same position.

The rule is simple: never construct a `Gson` directly. Use `JsonUtil.toJson` and
`JsonUtil.fromJson` everywhere.

## Timestamps read back wrong

**Symptom.** Metadata loads, but timestamps are null, zero, or a nested object
rather than an instant.

**Cause.** The value was written with `JsonUtil` and read with plain Gson, or the
reverse. Both sides need the adapter.

## Response body written twice

**Symptom.** An `IllegalStateException` about `getWriter` or `getOutputStream`
already being called.

**Cause.** A servlet response allows one or the other, never both. Downloads use
`getOutputStream()`. JSON uses `getWriter()`.

This is why a streaming download must be a handler route rather than a processor
route. See [chunked streaming](../storage/chunked-streaming.md).

## Related

- [Memory guarantee](../storage/memory-guarantee.md) — metadata and streaming rules
- [Storage backends](../storage/storage-backends.md) — where metadata is written
- [Runtime issues](./runtime-issues.md) — other runtime failures
