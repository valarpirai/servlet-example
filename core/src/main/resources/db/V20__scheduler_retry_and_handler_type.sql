-- Handler kind moves out of the handler text into its own column, and jobs gain retry settings.
--
-- handler_type   java: handler is a registered key.  script: handler is an api_scripts name.
-- max_retries    Retries after a failed run. 0 means never retry.
-- retry_delay_seconds       Wait before the first retry.
-- retry_exponential         TRUE doubles the wait on each further retry. FALSE keeps it fixed.
-- retry_max_delay_seconds   Ceiling on any one wait, jitter included.
-- retry_jitter              Fraction 0 to 1. 0.2 spreads each wait by plus or minus 20 percent.
-- retry_count               Retries used so far. Reset after a success or when retries run out.

ALTER TABLE scheduled_jobs
    ADD COLUMN IF NOT EXISTS handler_type            VARCHAR(10)  NOT NULL DEFAULT 'java'
        CHECK (handler_type IN ('java', 'script')),
    ADD COLUMN IF NOT EXISTS max_retries             INT          NOT NULL DEFAULT 0
        CHECK (max_retries >= 0),
    ADD COLUMN IF NOT EXISTS retry_delay_seconds     INT          NOT NULL DEFAULT 60
        CHECK (retry_delay_seconds > 0),
    ADD COLUMN IF NOT EXISTS retry_exponential       BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS retry_max_delay_seconds INT          NOT NULL DEFAULT 3600
        CHECK (retry_max_delay_seconds > 0),
    ADD COLUMN IF NOT EXISTS retry_jitter            NUMERIC(3,2) NOT NULL DEFAULT 0
        CHECK (retry_jitter >= 0 AND retry_jitter <= 1),
    ADD COLUMN IF NOT EXISTS retry_count             INT          NOT NULL DEFAULT 0;

UPDATE scheduled_jobs
SET handler_type = 'script', handler = substr(handler, 8)
WHERE handler LIKE 'script:%';
