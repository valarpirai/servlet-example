# Adding a Job

A job is a row in `scheduled_jobs` plus a handler. The handler is either a
Java class or a stored script.

## The row

```sql
INSERT INTO scheduled_jobs (name, handler, trigger_type, trigger_spec)
VALUES ('nightly-cleanup', 'script:cleanup', 'daily', '{"time": "02:30"}');
```

| Column | Meaning |
|---|---|
| `name` | Label for logs |
| `handler` | A registered Java key, or `script:<api_scripts name>` |
| `trigger_type` | `once`, `interval`, `daily`, `on_demand` |
| `trigger_spec` | JSON for the trigger type |
| `next_run` | First due time. `NULL` means due now |
| `priority` | Lower runs first. Default 100 |
| `context` | JSON state handed to the handler and saved after each run |
| `run_end` | Optional. Stop repeating after this time |
| `last_error` | Last failure. Cleared by the next success |

Times are UTC. The Table browser can edit the rows.

## A script handler

Store a script in `api_scripts`. Define `run(request, response)`.

```javascript
function run(request, response) {
  // request.body holds the job context
  response.setBody({ ok: true });
}
```

Point a job at it with `handler = 'script:<name>'`. The script runs in the
normal sandbox, with the same timeout and `require()` support as an API script.
A thrown error, or a response status of 400 or more, fails the run.

The script cannot change the context. The context passes through unchanged.

## A Java handler

Implement `JobHandler`. Register it under a key before the scheduler starts.

```java
handlers.register("cleanup", ctx -> {
  // work here
  return ctx; // saved as the next run's context
});
```

`SchedulerBootstrap.create` builds the `HandlerRegistry`. Add the `register`
call there. Then use `handler = 'cleanup'` in the row.

## Sample jobs

Migration `V18__seed_sample_jobs.sql` seeds two jobs that run every minute.

| Job | Handler | Shows |
|---|---|---|
| `hello-world` | Java: `HelloWorldJobHandler`, key `hello-world` | A log line and a run counter kept in the context |
| `hello-script` | Script: `api_scripts` row `hello-script` | A script response, logged by `ScriptJobHandler` |

Copy either row as a starting point. Delete them when you no longer want them.

## Run it sooner

Set `next_run` to now, or `NULL`. The scheduler sees it within
`scheduler.pollSeconds`. `on_demand` jobs never run on their own.

## Settings

All live in `app_properties`. See
[Configuration](../architecture/configuration.md) for the table.

## When a job does not run

| Symptom | Check |
|---|---|
| Never runs | `state` is 3, or `trigger_type` is `on_demand`, or `next_run` is in the future |
| `last_error` is `no handler: ...` | The Java key is not registered. The job retries in an hour |
| `trigger_type` turned into `on_demand` | `last_error` says why. Fix the spec, then reset the type |
| Stuck in state 1 or 2 | The sweep resets it within `scheduler.sweepMinutes` |
| Script job fails | `last_error` holds the script's message. Test it as an API script |
| Nothing runs at all | `scheduler.enabled` is false, or the log has no `Scheduler started` line |

## Related

- [Scheduler overview](./scheduler-overview.md) — parts, states, safety rules
- [API scripting](../scripting/api-scripting.md) — writing the script
- [Script execution](../scripting/script-execution.md) — sandbox and timeout
- [Configuration](../architecture/configuration.md) — the `scheduler.*` properties
