# Job Retries

A failed job can try again. Each job row sets how often and how long to wait.

Retries are off by default. `max_retries` is 0.

## The columns

| Column | Default | Meaning |
|---|---|---|
| `max_retries` | 0 | Retries after a failed run. 0 means never |
| `retry_delay_seconds` | 60 | Wait before the first retry |
| `retry_exponential` | true | True doubles the wait on each further retry. False keeps it fixed |
| `retry_max_delay_seconds` | 3600 | Ceiling on any one wait, jitter included |
| `retry_jitter` | 0 | Fraction from 0 to 1. 0.2 spreads each wait by plus or minus 20 percent |
| `retry_count` | 0 | Retries used so far. The scheduler owns it |

```sql
INSERT INTO scheduled_jobs
  (name, handler_type, handler, trigger_type, max_retries, retry_delay_seconds, retry_jitter)
VALUES ('sync-orders', 'java', 'sync-orders', 'once', 5, 30, 0.2);
```

## The wait

`RetryPolicy.delayMillis` computes it.

1. Start from `retry_delay_seconds`.
2. If exponential, multiply by 2 for each earlier retry. Retry 3 waits 4 times the base.
3. Multiply by a random factor between `1 - jitter` and `1 + jitter`.
4. Cut to `retry_max_delay_seconds`.

Jitter keeps many failing jobs from retrying at the same moment.

## What happens on a failure

| Situation | Result |
|---|---|
| Retries remain | Job returns to ready. `next_run` is the retry time. `retry_count` goes up |
| Retries used up, job repeats | `retry_count` resets. The normal next run applies |
| Retries used up, job is `once` | State becomes 3 (error). `retry_count` resets |
| The retry time passes `run_end` | No retry. The normal path applies |
| A run succeeds | `retry_count` resets. A `once` job is deleted |

A retry replaces the next scheduled run. An interval job that fails waits for the
retry, not for its next interval.

Every attempt writes a history row with status `failed`. See
[Monitoring](./monitoring.md). The `retried` counter counts attempts put back.

## What does not retry

- A missing handler. The job waits an hour and records `no handler`.
- A bad trigger spec. The job is parked as `on_demand`.

Both are setup errors. Retrying cannot fix them.

## Related

- [Scheduler overview](./scheduler-overview.md) — states and safety rules
- [Adding a job](./adding-a-job.md) — the row and handlers
- [Monitoring](./monitoring.md) — history and the `retried` counter
