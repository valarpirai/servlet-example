# Adding a Job

A job is a row in `scheduled_jobs` plus a handler. The handler is either a
Java class or a stored script.

## The row

```sql
INSERT INTO scheduled_jobs (name, handler_type, handler, trigger_type, trigger_spec)
VALUES ('nightly-cleanup', 'script', 'cleanup', 'daily', '{"time": "02:30"}');
```

| Column | Meaning |
|---|---|
| `name` | Label for logs and history |
| `handler_type` | `java` (the default) or `script` |
| `handler` | A `@JobName` for `java`. An `api_scripts` name for `script` |
| `trigger_type` | `once`, `interval`, `daily`, `on_demand` |
| `trigger_spec` | JSON for the trigger type |
| `next_run` | First due time. `NULL` means due now |
| `priority` | Lower runs first. Default 100 |
| `context` | JSON state handed to the handler and saved after each run |
| `run_end` | Optional. Stop repeating after this time |
| `last_error` | Last failure. Cleared by the next success |

Retry columns are in [Job retries](./job-retries.md). Times are UTC. The Table
browser can edit the rows.

## A Java handler

Implement `JobHandler`. Name it with `@JobName`. Give it a public no-argument
constructor.

```java
@JobName("cleanup")
public class CleanupJob implements JobHandler {
  @Override
  public JsonObject execute(JsonObject context) {
    // work here
    return context; // saved as the next run's context
  }
}
```

At startup `JobHandlerScanner` finds every `@JobName` class under `com.example`
with ClassGraph and registers it. Use `handler_type = 'java'` and
`handler = 'cleanup'` in the row.

The scan fails startup when a class is not a `JobHandler`, has no public
no-argument constructor, or shares a name with another class.

## A script handler

Store a script in `api_scripts`. Define `run(request, response)`.

```javascript
function run(request, response) {
  // request.body holds the job context
  response.setBody({ ok: true });
}
```

Use `handler_type = 'script'` and `handler = '<script name>'`. The script runs
in the normal sandbox, with the same timeout and `require()` support as an API
script. A thrown error, or a response status of 400 or more, fails the run.
`ScriptJobHandler` logs the response body.

The script cannot change the context. The context passes through unchanged.

## Sample jobs

Migration `V18__seed_sample_jobs.sql` seeds two jobs that run every minute.

| Job | Handler | Shows |
|---|---|---|
| `hello-world` | Java: `HelloWorldJobHandler`, `@JobName("hello-world")` | A log line and a run counter kept in the context |
| `hello-script` | Script: `api_scripts` row `hello-script` | A script response, logged by `ScriptJobHandler` |

Copy either row as a starting point. Delete them when you no longer want them.

## Run a one-time job from code

`Scheduler.get()` returns the running scheduler, or null when
`scheduler.enabled` is false.

```java
JsonObject input = new JsonObject();
input.addProperty("userId", 42);
Scheduler.get().enqueue("rebuild-report", Job.SCRIPT, "rebuild", input);
```

`enqueue` inserts a `once` row, wakes the scheduler and returns the job id. The
handler gets the input as its context. The row is deleted after a successful run.

For retries or a priority, build the `Job` and call `enqueue(Job)`. It sets the
id and the `once` trigger. Every other field keeps your value.

## Run it sooner

Set `next_run` to now, or `NULL`. The scheduler sees it within
`scheduler.pollSeconds`. `on_demand` jobs never run on their own.

## When a job does not run

| Symptom | Check |
|---|---|
| Never runs | `state` is 3, or `trigger_type` is `on_demand`, or `next_run` is in the future |
| `last_error` is `no handler: java x` | No `@JobName("x")` class exists. The job retries in an hour |
| `trigger_type` turned into `on_demand` | `last_error` says why. Fix the spec, then reset the type |
| Stuck in state 1 or 2 | The sweep resets it within `scheduler.sweepMinutes` |
| Script job fails | `last_error` holds the script's message. Test it as an API script |
| Nothing runs at all | `scheduler.enabled` is false, or the log has no `Scheduler started` line |

## Related

- [Scheduler overview](./scheduler-overview.md) — parts, states, safety rules
- [Job retries](./job-retries.md) — retry, backoff and jitter
- [Monitoring](./monitoring.md) — history, stats API, metrics
- [API scripting](../scripting/api-scripting.md) — writing the script
- [Script execution](../scripting/script-execution.md) — sandbox and timeout
- [Configuration](../architecture/configuration.md) — the `scheduler.*` properties
