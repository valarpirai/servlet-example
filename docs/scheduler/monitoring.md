# Scheduler Monitoring

Three views of the scheduler: a history table, a stats endpoint and the
`/metrics` report.

## Run history

Every run writes one row to `scheduled_job_runs`. A successful `once` job is
deleted, so this table is its only record.

| Column | Meaning |
|---|---|
| `job_id`, `job_name` | The job. Kept even after the job row is gone |
| `handler_type`, `handler` | What ran |
| `node`, `run_id` | Which node, and which run |
| `status` | `success`, `failed`, `no_handler` or `parked` |
| `error` | Failure text |
| `started_at`, `ended_at` | UTC times |
| `duration_ms` | Wall time of the run |

The sweep deletes rows older than `scheduler.historyDays` (default 30). A failed
history write is logged. It never fails the job.

## Stats endpoint

`GET /api/scheduler/stats` returns JSON.

| Field | Meaning |
|---|---|
| `enabled` | False when no scheduler runs. Nothing else is returned |
| `node` | This node's counters. See below |
| `table` | Job rows by state across the cluster: `ready`, `queued`, `running`, `error` |

`SchedulerHandler.handleStats` serves it. The `table` counts read the database.
The `node` counters come from memory.

## Node counters

| Counter | Meaning |
|---|---|
| `queued`, `running`, `held` | Jobs in the queue, on workers, and both together |
| `ran`, `failed` | Handler runs since start |
| `retried` | Failed runs put back for another attempt |
| `lostRaces` | Jobs another node won |
| `recovered` | Rows reset by startup recovery or the sweep |
| `claimed`, `lastClaimMillis` | Jobs claimed, and the last claim query time |
| `latenessAvgMillis`, `latenessMaxMillis` | Wait past the due time |
| `handlers` | Per `type:handler`: `runs`, `failures`, `avgMillis`, `maxMillis` |

Lateness is the number users feel. Watch it first.

## The metrics report

`/metrics` and the two-minute metrics log line carry the same `node` counters
under a `scheduler` key. The key is absent when no scheduler runs.
`SchedulerMetrics` holds the counters. `RuntimeMetrics.snapshot` adds them.

## Related

- [Scheduler overview](./scheduler-overview.md) — parts and states
- [Job retries](./job-retries.md) — what `retried` counts
- [Structured logging](../observability/structured-logging.md) — the log format
