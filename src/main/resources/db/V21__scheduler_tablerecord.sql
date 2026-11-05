-- The scheduler reads and writes its tables through TableRecord, so both tables join the allow-list.
-- Each entry is table:keyColumn.
UPDATE app_properties
SET value = value || ',scheduled_jobs:id,scheduled_job_runs:id'
WHERE name = 'record.allowedTables'
  AND value NOT LIKE '%scheduled_jobs:id%';

-- next_run is always set. A job with no due time now defaults to now, so the claim query needs
-- no IS NULL branch. Rows that held NULL (due now) become due at migration time.
UPDATE scheduled_jobs SET next_run = NOW() WHERE next_run IS NULL;

ALTER TABLE scheduled_jobs
    ALTER COLUMN next_run SET DEFAULT NOW(),
    ALTER COLUMN next_run SET NOT NULL;
