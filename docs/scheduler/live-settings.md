# Live Scheduler Settings

Change `scheduler.workers` and the pool resizes. No restart.

## What it does

A business rule on `app_properties` watches every `scheduler.*` row. When one is
inserted or updated, the rule calls `Scheduler.reload()`. The scheduler on that node
reads its settings again and applies them.

The rule is `Reload scheduler settings`, seeded by migration `V23`. Migration `V24` makes
it an [async rule](../scripting/async-business-rules.md), so it runs on a scheduler
worker and never inside the request that changed the setting. Its condition and script:

```javascript
// condition
String(current.getValue('name')).indexOf('scheduler.') === 0
// script
if (typeof Scheduler !== 'undefined') { Scheduler.reload(); }
```

Edit a property through the table browser, the table API or a script. The rule fires
on all three. See [Table editing](../data-browser/table-editing.md).

## What changes at once

| Setting | Effect |
|---|---|
| `scheduler.workers` | Grows or shrinks the pool. Limits: 1 to 64 |
| `scheduler.maxInFlight` | The cap on queued plus running jobs |
| `scheduler.claimBatch` | Jobs claimed per poll |
| `scheduler.pollSeconds` | The wait between polls. The scheduler wakes at once |
| `scheduler.sweepMinutes` | The wait between stuck-job sweeps |
| `scheduler.historyDays` | How long run history stays |

`scheduler.enabled` and `scheduler.stopSeconds` need a restart.

## How the pool resizes

- **Growing:** new `scheduler-worker-N` threads start at once.
- **Shrinking:** the highest-numbered workers retire. An idle worker stops at once. A
  busy worker finishes its job first. A running job is never interrupted.
- The log shows `Scheduler settings reloaded` with the new values.
  `/api/scheduler/stats` reports `workers`, the count that are not retiring.

## Limits

- **One node.** The job runs on the node that claims it. Other nodes keep their old
  settings until they restart or run `Scheduler.reload()` themselves.
- **A short delay.** The change applies when a worker claims the job, usually within a
  second.
- **No scheduler at all.** Async rules need a running scheduler. With none, the reload
  job waits in the table.
- **Turn it off.** Set `active` to false on the rule in `business_rules`.

## Calling it yourself

```javascript
Scheduler.reload();   // "workers 4 -> 6, maxInFlight 100, claimBatch 20, pollMillis 1000"
```

## Related

- [Scheduler overview](./scheduler-overview.md) — how the scheduler works
- [Monitoring](./monitoring.md) — stats and history
- [Table records and business rules](../scripting/table-records-and-rules.md) — how rules run
