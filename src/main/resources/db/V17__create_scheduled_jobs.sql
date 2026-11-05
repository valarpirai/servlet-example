-- Scheduled jobs. The table is the truth; the in-memory queue is a cache of it.
--
-- handler       a registered Java handler key, or 'script:<name>' for an api_scripts row
--               (the script's run(request, response) function is called)
-- trigger_type  once | interval | daily | on_demand
-- trigger_spec  interval: {"seconds": 60}   daily: {"time": "02:30"} in UTC
-- state         0 ready, 1 queued, 2 running, 3 error
-- next_run      NULL means due now. on_demand rows are never claimed.
-- priority      lower runs first

CREATE TABLE IF NOT EXISTS scheduled_jobs (
    id           VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name         VARCHAR(255) NOT NULL,
    handler      VARCHAR(255) NOT NULL,
    trigger_type VARCHAR(20)  NOT NULL CHECK (trigger_type IN ('once', 'interval', 'daily', 'on_demand')),
    trigger_spec TEXT         NOT NULL DEFAULT '{}',
    next_run     TIMESTAMPTZ,
    state        INT          NOT NULL DEFAULT 0,
    priority     INT          NOT NULL DEFAULT 100,
    owner        VARCHAR(255),
    run_id       VARCHAR(36),
    context      TEXT         NOT NULL DEFAULT '{}',
    last_error   TEXT,
    run_end      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scheduled_jobs_claim ON scheduled_jobs (state, next_run, priority);

INSERT INTO app_properties (name, value, type, description) VALUES
  ('scheduler.enabled',       'true', 'BOOLEAN', 'Start the job scheduler at boot'),
  ('scheduler.workers',       '4',    'INTEGER', 'Worker threads that run jobs'),
  ('scheduler.maxInFlight',   '100',  'INTEGER', 'Most jobs queued plus running at once'),
  ('scheduler.claimBatch',    '20',   'INTEGER', 'Most jobs claimed from the table per poll'),
  ('scheduler.pollSeconds',   '1',    'INTEGER', 'Seconds between polls of the jobs table'),
  ('scheduler.sweepMinutes',  '5',    'INTEGER', 'Minutes between stuck-job sweeps'),
  ('scheduler.stopSeconds',   '30',   'INTEGER', 'Seconds to let running jobs finish at shutdown')
ON CONFLICT (name) DO NOTHING;
