-- One row per run of a scheduled job. The job row can be gone (a successful once job is deleted),
-- so each history row keeps its own copy of the job id, name and handler.
--
-- status: success | failed | no_handler | parked
-- Rows older than scheduler.historyDays are deleted by the scheduler's sweep.

CREATE TABLE IF NOT EXISTS scheduled_job_runs (
    id          BIGSERIAL    PRIMARY KEY,
    job_id      VARCHAR(36)  NOT NULL,
    job_name    VARCHAR(255) NOT NULL,
    handler_type VARCHAR(10) NOT NULL,
    handler     VARCHAR(255) NOT NULL,
    node       VARCHAR(255) NOT NULL,
    run_id      VARCHAR(36)  NOT NULL,
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('success', 'failed', 'no_handler', 'parked')),
    error       TEXT,
    started_at  TIMESTAMPTZ  NOT NULL,
    ended_at    TIMESTAMPTZ  NOT NULL,
    duration_ms BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_scheduled_job_runs_job ON scheduled_job_runs (job_id, started_at);
CREATE INDEX IF NOT EXISTS idx_scheduled_job_runs_started ON scheduled_job_runs (started_at);

INSERT INTO app_properties (name, value, type, description) VALUES
  ('scheduler.historyDays', '30', 'INTEGER', 'Days of job run history to keep')
ON CONFLICT (name) DO NOTHING;

INSERT INTO routes
    (path, methods, type, handler, handler_method, processor, resource, content_type, description, path_params, sort_order, is_default)
SELECT * FROM (VALUES
    ('/api/scheduler/stats', 'GET', 'handler', 'SchedulerHandler', 'handleStats', NULL, NULL, 'application/json', 'Job scheduler statistics: this node''s counters and job row counts by state', NULL, 275, TRUE)
) AS v (path, methods, type, handler, handler_method, processor, resource, content_type, description, path_params, sort_order, is_default)
WHERE NOT EXISTS (
    SELECT 1 FROM routes r WHERE r.path = v.path AND r.methods = v.methods
);
