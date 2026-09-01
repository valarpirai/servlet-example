# Storage Backends

Two backends implement `AttachmentStorage`. `storage.type` picks one.

The default is `database`.

| Value | Class | Where bytes go |
|---|---|---|
| `database` | `DatabaseAttachmentStorage` | `attachment_chunks` table, BYTEA rows |
| `filesystem` | `LocalFileSystemStorage` | Files under `storage.filesystem.path` |

`AttachmentManager` sits in front of both. It caches metadata and hides the
choice from callers. Handlers and processors never name a backend.

## Database backend

Two tables, created by `db/V3__create_attachments.sql`.

`attachments` holds metadata: id, file name, content type, size, SHA-256 hash,
storage type, timestamps.

`attachment_chunks` holds the bytes, one row per chunk, with a position column.
The foreign key cascades, so deleting an attachment deletes its chunks.

```sql
attachment_chunks (
  id            BIGSERIAL PRIMARY KEY,
  attachment_id VARCHAR(36) REFERENCES attachments (id) ON DELETE CASCADE,
  position      INT,
  data          BYTEA,
  UNIQUE (attachment_id, position)
)
```

Writes run inside one transaction. A failed upload leaves no partial rows.

## Filesystem backend

One directory per attachment, named by id. Chunks are numbered files, metadata
sits alongside as JSON.

```
data/attachments/
└── {uuid}/
    ├── chunk_0
    ├── chunk_1
    └── metadata.json
```

The metadata JSON carries `Instant` fields, so it must be written and read with
`JsonUtil`. A bare `new Gson()` produces unusable timestamps.

## Choosing

The database backend keeps everything in one place, so backups and transactions
cover attachments too. It costs database size and write throughput.

The filesystem backend is cheaper per byte and simpler to inspect. It does not
survive a container restart unless the path is a mounted volume, and it does not
work across multiple instances without shared storage.

## Adding a backend

Implement `AttachmentStorage`, then have `AttachmentManager` construct it for a
new `storage.type` value. Keep the chunk callback shape — write one chunk at a
time and never hold more.

Seeded properties already reserve `storage.s3.bucket` and `storage.s3.region`
for an S3 backend.

## Related

- [Chunked streaming](./chunked-streaming.md) — the shared write path
- [Memory guarantee](./memory-guarantee.md) — the constraint any backend must keep
- [Configuration](../architecture/configuration.md) — storage properties
