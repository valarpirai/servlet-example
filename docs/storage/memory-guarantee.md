# Memory Guarantee

Heap usage scales with concurrent requests, not with file size.

A 500 MB file moves through 1 MB of heap. Ten concurrent uploads of any size use
about 10 MB. This holds because no code path ever holds a whole file.

## The rule

Never load a file into memory. Not on upload, not on download, not to hash it,
not to copy it.

```java
// Wrong. A 500 MB file becomes a 500 MB array.
byte[] fileData = Files.readAllBytes(path);

// Right. One reused buffer, whatever the file size.
byte[] buffer = new byte[chunkSize];
while ((bytesRead = inputStream.read(buffer)) != -1) {
  writeChunk(buffer, bytesRead);
}
```

`Files.readAllBytes`, `InputStream.readAllBytes` and any `byte[]` sized from
file length are all the same bug.

## What this buys

| Concurrent uploads | Heap for file data |
|---|---|
| 1 | 1 MB |
| 10 | 10 MB |
| 100 | 100 MB |
| 1000 | 1000 MB |

Memory grows with concurrency, and concurrency is bounded by
`threadPool.maxThreads`. File size does not enter the calculation.

The naive approach fails at two or three concurrent large uploads on a 1 GB
heap. This approach does not fail.

## Configured limits

Chunk size is `storage.chunkSize`, 1 MB by default. Smaller chunks mean less
memory and more I/O calls.

Upload size is capped separately by `upload.maxFileSize`, 10 MB by default, and
`upload.maxRequestSize`, 50 MB. These are policy limits, not memory limits.
Raising them costs no heap.

## Checking it

`/metrics` reports actual heap use.

```bash
curl http://localhost:8080/metrics
```

Upload a file at the configured maximum and watch the number. It should barely
move. If it tracks file size, something loads the whole file.

Run the storage tests after any change here:

```bash
mvn test -Dtest=*Storage*Test,AttachmentManagerTest
```

## Metadata is JSON, and needs JsonUtil

Attachment metadata carries `java.time.Instant` fields. Use `JsonUtil.toJson`
and `JsonUtil.fromJson`, never a bare `new Gson()` — see
[JSON and serialization](../troubleshooting/json-and-serialization.md).

## Related

- [Chunked streaming](./chunked-streaming.md) — how chunking works
- [Storage backends](./storage-backends.md) — database and filesystem
- [Configuration](../architecture/configuration.md) — the size properties
