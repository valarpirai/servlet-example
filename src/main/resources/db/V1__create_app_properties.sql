CREATE TABLE IF NOT EXISTS app_properties (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    value       TEXT,
    type        VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by  VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_app_properties_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_app_properties_active ON app_properties (active);
