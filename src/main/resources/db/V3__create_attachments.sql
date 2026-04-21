CREATE TABLE IF NOT EXISTS attachments (
    id           VARCHAR(36)  PRIMARY KEY,
    file_name    VARCHAR(500) NOT NULL,
    content_type VARCHAR(200),
    size_bytes   BIGINT       NOT NULL DEFAULT 0,
    hash         VARCHAR(64),
    storage_type VARCHAR(50)  NOT NULL DEFAULT 'database',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS attachment_chunks (
    id             BIGSERIAL   PRIMARY KEY,
    attachment_id  VARCHAR(36) NOT NULL REFERENCES attachments (id) ON DELETE CASCADE,
    position       INT         NOT NULL,
    data           BYTEA       NOT NULL,
    CONSTRAINT uq_attachment_chunk_position UNIQUE (attachment_id, position)
);

CREATE INDEX IF NOT EXISTS idx_attachment_chunks_lookup
    ON attachment_chunks (attachment_id, position);
