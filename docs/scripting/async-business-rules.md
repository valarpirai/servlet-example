# Async Business Rules

An async rule runs after a write, off the request thread, as a scheduler job.

## What it does

Set `run_when` to `async` on a row of `business_rules`. When a matching write
finishes, `BusinessRuleRunner` checks the rule's condition. If it holds, a job is
saved. A scheduler worker runs the rule's script shortly after, on its own thread.

The request never waits for the script. A slow or failing script cannot fail or delay
the write.

## Writing one

```javascript
BusinessRules.create({
  name: 'Tell the audit log', collection: 'js_modules', runWhen: 'async',
  onInsert: true, onUpdate: true,
  condition: "current.getValue('name').indexOf('utils/') === 0",
  script: "var log = new TableRecord('audit_log');" +
          "log.setValue('what', 'module ' + current.getValue('name'));" +
          "log.insert();"
});
```

The columns are the same as a `before` or `after` rule. See
[Table records and business rules](./table-records-and-rules.md).

## What the script sees

- `current` is a read-only snapshot of the row after the write. For a delete, it is the
  row that was deleted.
- `previous` is a read-only snapshot of the row before the write. It is null on insert.
- `TableRecord` works as in any rule.

`setAbortAction` does nothing. The write is done. A write through `current` or
`previous` throws. Query the table for fresher data.

## When it runs

1. The write finishes.
2. The runner finds active async rules for the table and the write.
3. It evaluates each condition now, on the saved fields. A false condition queues
   nothing, so a rule that does not apply costs no job.
4. It saves one job per rule, with handler `business-rule` and the saved write as context.
5. A scheduler worker claims the job, usually within a second, and runs the script.

A rule that is deleted, switched off, or no longer async by the time the job runs is
skipped.

## Failures and retries

A script that throws fails the job. The job retries `record.asyncRuleRetries` times
(default 3), waiting `record.asyncRuleRetryDelaySeconds` (default 30) and doubling.
History is in `scheduled_job_runs`, and stats are at `/api/scheduler/stats`.

The job may run twice. Write the script so a second run is harmless.

## Rules that write rows

A write made by an async script fires its own rules, async ones included. Each async
step adds one to a depth counter carried in the job. At `record.maxRuleDepth`
(default 5) further async rules are skipped and a warning is logged.

## Limits

- **Needs the scheduler.** The job waits in the table until some node runs a scheduler.
  Without one, async rules never run.
- **Any node runs it.** The node that claims the job, not the one that took the write.
- **One extra query per write.** The runner looks for async rules after every write.
- **Not for blocking.** Use a `before` rule to stop a write.

## Related

- [Table records and business rules](./table-records-and-rules.md) — sync rules
- [Managing business rules](./managing-business-rules.md) — create and edit rules
- [Job retries](../scheduler/job-retries.md) — how a failed job retries
- [Live scheduler settings](../scheduler/live-settings.md) — an async rule in use
