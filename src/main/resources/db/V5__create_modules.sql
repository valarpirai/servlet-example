-- JavaScript modules move from data/modules/*.js into a table.
-- name is the module path without the .js suffix, e.g. 'utils/math'.

CREATE TABLE IF NOT EXISTS modules (
    name       VARCHAR(500) PRIMARY KEY,
    content    TEXT         NOT NULL,
    size_bytes INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- The storage directory no longer exists.
DELETE FROM app_properties WHERE name = 'modules.directory';
