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

`TableRecord` touches only the tables in the `record.allowedTables` property, as
`table:keyColumn` pairs. A new table needs a migration that appends its pair.

## Scope

The rule covers new code. It does not require migrating existing direct jOOQ use.
The data-access layer itself is exempt: `TableRecord`, `Database` and
`BusinessRuleRunner`.

## Related

- [Table records and business rules](../../docs/scripting/table-records-and-rules.md) — the API
- [Package map](../../docs/architecture/package-map.md) — where `record/` lives
- [Project rules](../../CLAUDE.md) — repository-wide guidance
