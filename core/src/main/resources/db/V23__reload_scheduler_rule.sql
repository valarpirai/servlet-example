-- A scheduler.* setting changed in app_properties: apply it to the running scheduler.
-- The Scheduler global exists when the scheduler module is on the classpath, so the
-- script checks first. The rule resizes the worker pool on the node that took the write.
INSERT INTO business_rules
    (name, collection, run_when, on_insert, on_update, on_delete, condition, script, order_no)
SELECT 'Reload scheduler settings', 'app_properties', 'after', TRUE, TRUE, FALSE,
       'String(current.getValue(''name'')).indexOf(''scheduler.'') === 0',
       'if (typeof Scheduler !== ''undefined'') { Scheduler.reload(); }',
       100
WHERE NOT EXISTS (SELECT 1 FROM business_rules WHERE name = 'Reload scheduler settings');
