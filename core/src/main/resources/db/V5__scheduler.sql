-- Scheduled jobs. The table is the truth; the in-memory queue is a cache of it.
--
-- handler       a registered Java handler key, or an api_scripts row name when
--               handler_type is 'script' (its run(request, response) function is called)
-- handler_type  java | script
-- trigger_type  once | interval | daily | on_demand
-- trigger_spec  interval: {"seconds": 60}   daily: {"time": "02:30"} in UTC
-- state         0 ready, 1 queued, 2 running, 3 error
-- next_run      on_demand rows are never claimed
-- priority      lower runs first
-- retry_*       how a failed run waits before the next try; retry_count is the tries so far
CREATE TABLE scheduled_jobs (
    id                      VARCHAR(36)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name                    VARCHAR(255)  NOT NULL,
    handler                 VARCHAR(255)  NOT NULL,
    handler_type            VARCHAR(10)   NOT NULL DEFAULT 'java'
                            CHECK (handler_type IN ('java', 'script')),
    trigger_type            VARCHAR(20)   NOT NULL
                            CHECK (trigger_type IN ('once', 'interval', 'daily', 'on_demand')),
    trigger_spec            TEXT          NOT NULL DEFAULT '{}',
    next_run                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    state                   INTEGER       NOT NULL DEFAULT 0,
    priority                INTEGER       NOT NULL DEFAULT 100,
    owner                   VARCHAR(255),
    run_id                  VARCHAR(36),
    context                 TEXT          NOT NULL DEFAULT '{}',
    last_error              TEXT,
    run_end                 TIMESTAMPTZ,
    max_retries             INTEGER       NOT NULL DEFAULT 0 CHECK (max_retries >= 0),
    retry_delay_seconds     INTEGER       NOT NULL DEFAULT 60 CHECK (retry_delay_seconds > 0),
    retry_exponential       BOOLEAN       NOT NULL DEFAULT TRUE,
    retry_max_delay_seconds INTEGER       NOT NULL DEFAULT 3600 CHECK (retry_max_delay_seconds > 0),
    retry_jitter            NUMERIC(3, 2) NOT NULL DEFAULT 0
                            CHECK (retry_jitter >= 0 AND retry_jitter <= 1),
    retry_count             INTEGER       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scheduled_jobs_claim ON scheduled_jobs (state, next_run, priority);

-- One row per run of a scheduled job. The job row can be gone (a successful once job is
-- deleted), so each history row keeps its own copy of the job id, name and handler.
-- Rows older than scheduler.historyDays are deleted by the scheduler's sweep.
--
-- status: success | failed | no_handler | parked
CREATE TABLE scheduled_job_runs (
    id           BIGSERIAL    PRIMARY KEY,
    job_id       VARCHAR(36)  NOT NULL,
    job_name     VARCHAR(255) NOT NULL,
    handler_type VARCHAR(10)  NOT NULL,
    handler      VARCHAR(255) NOT NULL,
    node         VARCHAR(255) NOT NULL,
    run_id       VARCHAR(36)  NOT NULL,
    status       VARCHAR(20)  NOT NULL
                 CHECK (status IN ('success', 'failed', 'no_handler', 'parked')),
    error        TEXT,
    started_at   TIMESTAMPTZ  NOT NULL,
    ended_at     TIMESTAMPTZ  NOT NULL,
    duration_ms  BIGINT       NOT NULL
);

CREATE INDEX idx_scheduled_job_runs_job     ON scheduled_job_runs (job_id, started_at);
CREATE INDEX idx_scheduled_job_runs_started ON scheduled_job_runs (started_at);

-- Two sample jobs that run every minute.
--   hello-world   Java handler registered in SchedulerBootstrap
--   hello-script  JavaScript handler, the api_scripts row of the same name
INSERT INTO scheduled_jobs (name, handler, handler_type, trigger_type, trigger_spec, priority) VALUES
  ('hello-world',  'hello-world',  'java',   'interval', '{"seconds": 60}', 100),
  ('hello-script', 'hello-script', 'script', 'interval', '{"seconds": 60}', 100);
