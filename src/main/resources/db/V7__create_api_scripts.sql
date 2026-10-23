-- Scripted REST endpoints move from scripts/api/*.js into a table.
-- name is the endpoint name, so 'hello' answers /api/v1/hello.
-- Shared helpers from scripts/lib and scripts/vendor live in the modules table
-- as lib/<name> and vendor/<name>.

CREATE TABLE IF NOT EXISTS api_scripts (
    name       VARCHAR(255) PRIMARY KEY,
    content    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
