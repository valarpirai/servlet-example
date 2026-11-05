# Data Access Rules

Rules for any Java code that reads or writes a database table. Read these before
writing a repository, a handler or a processor that touches the database.

## The rule

- MUST read and write tables through `TableRecord` (`com.example.record`).
- MUST NOT use the jOOQ DSL (`dsl.select`, `dsl.update`, `DSL.field`, and so on)
  in application code.
- MUST NOT write raw JDBC. `Database.openConnection()` stays for streaming cursors.

`TableRecord` is the one data-access layer. It checks table and column names, and
it runs business rules around each write.

## When TableRecord lacks a capability

Extend `TableRecord`. Do not drop to jOOQ for one statement.

- Add the capability as a general method, named like the matching GlideRecord one.
- Document it in `docs/scripting/table-records-and-rules.md`.
- Test it in `TableRecordTest`.

Every public method of `TableRecord` is also visible to scripts. Treat a new method
as a script API.

## Allow-list

`TableRecord` works on any table. `record.allowedTables` in `application.yml` (env
`RECORD_ALLOWED_TABLES`) is an optional restriction. Unset means every table. It never
lives in `app_properties`.

ACL checks will live in a `TableRecordSecure` wrapper that delegates to `TableRecord`.
Do not add them to `TableRecord`.

## Scope

The rule covers new code. It does not require migrating existing direct jOOQ use.
The data-access layer itself is exempt: `TableRecord`, `Database` and
`BusinessRuleRunner`. Two more classes stay on jOOQ for a stated reason:

- `DatabaseAttachmentStorage` writes binary chunks in one transaction.
  `TableRecord` returns strings and has no transaction API.
- `TableApi` browses any table through `information_schema`. A restricted
  `TableRecord` could not serve it.

## Related

- [Table records and business rules](../../docs/scripting/table-records-and-rules.md) — the API
- [Package map](../../docs/architecture/package-map.md) — where `record/` lives
- [Project rules](../../CLAUDE.md) — repository-wide guidance
