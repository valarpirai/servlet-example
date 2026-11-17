-- Two sample jobs that run every minute.
--   hello-world   Java handler registered in SchedulerBootstrap
--   hello-script  JavaScript handler, the api_scripts row of the same name

INSERT INTO api_scripts (name, content) VALUES
  ('hello-script',
   'function run(request, response) {
  response.setBody({ message: "Hello World from a JavaScript job", method: request.method });
}')
ON CONFLICT (name) DO NOTHING;

INSERT INTO scheduled_jobs (name, handler, trigger_type, trigger_spec, priority)
SELECT 'hello-world', 'hello-world', 'interval', '{"seconds": 60}', 100
WHERE NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE name = 'hello-world');

INSERT INTO scheduled_jobs (name, handler, trigger_type, trigger_spec, priority)
SELECT 'hello-script', 'script:hello-script', 'interval', '{"seconds": 60}', 100
WHERE NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE name = 'hello-script');
