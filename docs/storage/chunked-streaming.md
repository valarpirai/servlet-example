# Chunked Streaming

How a file moves in and out without being held in memory.

Files are split into fixed-size chunks. One chunk exists in heap at a time. The
chunk size is `storage.chunkSize`, 1 MB by default.

## Writing

`ChunkedOutputStream` wraps a 1 MB buffer and a callback. It fills the buffer,
hands it to the callback, then reuses it.

```
HTTP request InputStream
    ↓
ChunkedOutputStream (one buffer, reused)
    ├─ buffer full  → write chunk 0
    ├─ buffer full  → write chunk 1
    └─ close        → write final partial chunk

Peak heap: one chunk
```

The callback decides where a chunk goes. That is the whole difference between
backends: the filesystem backend writes a file, the database backend inserts a
row.

Hashing happens in the same pass. Each chunk updates a `MessageDigest` as it is
written, so the SHA-256 is finished when the last chunk lands. The file is never
re-read to hash it.

`ChunkedOutputStream` also tracks total bytes written and chunk count, which is
where `size_bytes` comes from. It is not trusted from the client.

## Reading

Reading reverses it. One chunk is fetched, streamed to the response, released,
then the next is fetched.

```
GET /api/attachment/{id}/download
    ↓
chunk 0 → response → released
chunk 1 → response → released
    ↓
HTTP response, chunked transfer encoding

Peak heap: one chunk
```

## Downloads use the output stream

A download writes bytes with `response.getOutputStream()`. JSON responses use
`response.getWriter()`. A servlet response allows one or the other, never both,
and mixing them throws at runtime.

This is why downloads are handler routes, not processor routes. A processor
returns a value, so it cannot stream.

## Ordering

Chunks carry an explicit position. The database backend has a unique constraint
on `(attachment_id, position)` and an index for lookup, so retrieval is ordered
by position rather than by insertion order.

## Related

- [Memory guarantee](./memory-guarantee.md) — why this matters
- [Storage backends](./storage-backends.md) — where chunks land
- [Request flow](../architecture/request-flow.md) — handler versus processor
