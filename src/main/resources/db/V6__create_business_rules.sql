-- Business rules: JavaScript that runs before or after a TableRecord write.
-- collection is the table name. condition is a JavaScript expression; empty means always run.

CREATE TABLE IF NOT EXISTS business_rules (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    collection VARCHAR(255) NOT NULL,
    run_when   VARCHAR(10)  NOT NULL CHECK (run_when IN ('before', 'after')),
    on_insert  BOOLEAN      NOT NULL DEFAULT FALSE,
    on_update  BOOLEAN      NOT NULL DEFAULT FALSE,
    on_delete  BOOLEAN      NOT NULL DEFAULT FALSE,
    condition  TEXT,
    script     TEXT         NOT NULL,
    order_no   INT          NOT NULL DEFAULT 100,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_business_rules_collection ON business_rules (collection, active);

-- Tables TableRecord may touch, each with its key column: table:key,table:key
INSERT INTO app_properties (name, value, type, description) VALUES
  ('record.allowedTables', 'modules:name', 'STRING', 'Tables TableRecord can read and write, as table:keyColumn pairs'),
  ('record.maxRuleDepth',  '5',            'INTEGER', 'Maximum nesting of business rules triggered by other rules')
ON CONFLICT (name) DO NOTHING;
