# Managing Jobs

`Jobs` creates, reads, changes, runs and deletes background jobs from Java and
from scripts.

## What it does

A job is a row in `scheduled_jobs`. `Jobs` wraps that table. It checks a job
before saving it, so a bad trigger never reaches the scheduler.

Java code uses `new Jobs()`. Scripts use the `Jobs` global. The HTTP route is the
generic table API on `scheduled_jobs`. See
[Table editing](../data-browser/table-editing.md). For the row layout, see
[Adding a job](./adding-a-job.md).

## From a script

```javascript
var id = Jobs.create({
  name: 'nightly-cleanup', handlerType: 'script', handler: 'cleanup',
  triggerType: 'daily', triggerSpec: { time: '02:30' }
});
var job = Jobs.get(id);              // job.name, job.stateName, job.nextRun
var all = Jobs.list();               // a Java list, soonest due first
Jobs.update(id, { priority: 10 });
var run = Jobs.runNow(id);           // a one-time copy runs now
var runs = Jobs.history(run, 5);     // newest first
Jobs.delete(id);
Jobs.enqueue('report', 'java', 'hello', { userId: 42 });
```

## From Java

```java
Jobs jobs = new Jobs();
String id = jobs.enqueue("report", "java", "hello", Map.of("userId", 42));
JobInfo info = jobs.get(id);
```

## Methods

| Method | Returns |
|---|---|
| `create(job or fields)` | The new id. The first run is `nextRun`, or now |
| `enqueue(name, handlerType, handler, context)` | The id of a one-time job that runs now |
| `runNow(id)` | The id of a one-time copy. The job keeps its schedule |
| `get(id)` | A `JobInfo`, or null |
| `list()`, `listByState(state)` | `JobInfo` list, soonest due first. State is 0 to 3 |
| `update(id, changes)` | true, or false when a node holds the job |
| `delete(id)` | true, or false when the job is missing or held |
| `history(id, limit)` | `JobRun` list, newest first |
| `validate(job or fields)` | Nothing. Throws when the job is invalid |

Field names are `name`, `handlerType`, `handler`, `triggerType`, `triggerSpec`,
`nextRun`, `priority`, `context`, `runEnd`, `maxRetries`, `retryDelaySeconds`,
`retryExponential`, `retryMaxDelaySeconds` and `retryJitter`. An unknown name
throws. `triggerSpec` and `context` take an object or JSON text. `nextRun` and
`runEnd` take ISO text in UTC.

`JobInfo` carries the same fields as text and numbers. `stateName` is `ready`,
`queued`, `running` or `error`.

## Validation

`create` and `update` check these first:

- The name is not blank.
- `handlerType` is `java` or `script`, and the handler is not blank.
- `triggerType` is `once`, `interval`, `daily` or `on_demand`.
- The trigger spec fits the type. `on_demand` needs none.
- Retry counts and delays are positive. `retryJitter` is 0 to 1.

A failure throws `IllegalArgumentException`. Nothing is saved. The check does not
confirm that the handler exists. A missing handler shows up at run time as a
`no_handler` run.

## Held jobs

A queued or running job belongs to a node. `update` and `delete` leave it alone and
return false. Try again after the run ends.

`update` puts the job back to ready and clears its last error and retry count. Edit
an errored job to retry it.

## Related

- [Adding a job](./adding-a-job.md) — the row and the handlers
- [Job retries](./job-retries.md) — the retry columns
- [Monitoring](./monitoring.md) — history and stats
- [Registering a script global](../scripting/script-globals.md) — how `Jobs` reaches scripts
