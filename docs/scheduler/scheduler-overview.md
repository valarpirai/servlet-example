# Scheduler Overview

This is what the scheduler does. It runs jobs stored in the `scheduled_jobs`
table on a fixed pool of worker threads.

The table is the truth. The in-memory queue is a cache of it. A crash loses
memory, never a job.

## The parts

| Part | Count | Job |
|---|---|---|
| `scheduled_jobs` table | 1 | Stores every job: due time, state, owner, priority |
| Scheduler thread | 1 | Polls for due jobs, claims them, fills the queue |
| `JobQueue` | 1 | Hands jobs out by priority, then due time |
| Worker threads | `scheduler.workers` | Take a job, run it, write the next due time |

`Scheduler` owns the threads. `SqlJobRepository` owns every SQL statement.
`SchedulerBootstrap` builds both from `app_properties` and `Main` starts them.

## One job, start to finish

1. The scheduler thread selects ready jobs that are due and unowned.
2. For each, it updates the row to queued, owned by this node. The update
   carries `state = ready` as a filter. Zero rows changed means another node won.
3. It adds the job to the queue.
4. A worker takes the job and moves the row to running with a fresh run id.
5. The worker looks up the handler and runs it.
6. One update writes the next due time, context, error and clears the owner.

## States

| State | Value | Meaning |
|---|---|---|
| Ready | 0 | Waiting for its due time. No owner |
| Queued | 1 | Claimed. Sits in one node's queue |
| Running | 2 | A worker is executing it |
| Error | 3 | Failed and does not repeat. Needs a person |

## Trigger types

A `Recurrence` computes the next due time after each run.

| `trigger_type` | `trigger_spec` | Next run |
|---|---|---|
| `once` | `{}` | None. Deleted after success, marked Error after failure |
| `interval` | `{"seconds": 60}` | Previous due time plus the interval. A job that fell behind skips to now plus the interval |
| `daily` | `{"time": "02:30"}` | Next occurrence of the time, in UTC |
| `on_demand` | `{}` | Never claimed. Parked jobs end here |

A job with `run_end` set stops repeating once the next run passes that time.
Failed repeating jobs keep their schedule. The error stays in `last_error`.

## Safety rules

- **No lock.** Every state change filters on the state it expects. A stale
  writer changes zero rows.
- **Cap in flight.** Queued plus running never exceeds `scheduler.maxInFlight`.
- **Sweep.** Every `scheduler.sweepMinutes`, rows this node owns as queued or
  running but holds in no memory go back to ready. Startup runs the same reset.
- **Hand back.** Shutdown closes the queue and returns queued jobs to ready.
  Running jobs get `scheduler.stopSeconds` to finish, then are interrupted.
- **Missing handler.** The job returns to ready, `last_error` says why, and the
  next run moves out one hour.
- **Bad job.** An unknown trigger type or unreadable spec parks the job as
  `on_demand` with the error text.
- **Catch everything.** The scheduler loop and the worker loop catch every
  `Throwable`. One bad job never kills a thread.

## Related

- [Adding a job](./adding-a-job.md) — rows, Java handlers, script handlers
- [Package map](../architecture/package-map.md) — where the code lives
- [Configuration](../architecture/configuration.md) — the `scheduler.*` properties
- [Startup flow](../architecture/startup-flow.md) — where the scheduler starts
