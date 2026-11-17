-- Business rules may run async: after the write, off the request thread, as a scheduler job.
--
-- run_when       before | after | async
-- record.asyncRuleRetries / record.asyncRuleRetryDelaySeconds   retry settings of the job
--
-- The scheduler reload rule moves to async. It applies a setting change on a scheduler
-- worker, not inside the request that changed the setting.

ALTER TABLE business_rules DROP CONSTRAINT IF EXISTS business_rules_run_when_check;
ALTER TABLE business_rules
    ADD CONSTRAINT business_rules_run_when_check CHECK (run_when IN ('before', 'after', 'async'));

UPDATE business_rules
   SET run_when = 'async', updated_at = NOW()
 WHERE name = 'Reload scheduler settings'
   AND collection = 'app_properties'
   AND run_when = 'after';

INSERT INTO app_properties (name, value, type, description) VALUES
  ('record.asyncRuleRetries',            '3',  'INTEGER', 'Retries of an async business rule after a failed run'),
  ('record.asyncRuleRetryDelaySeconds',  '30', 'INTEGER', 'Seconds before the first retry of an async business rule')
ON CONFLICT (name) DO NOTHING;
