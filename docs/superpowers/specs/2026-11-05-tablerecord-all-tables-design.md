# TableRecord on all tables: design

Project: `~/dev/expr/servlet-example`. Status: draft, awaiting approval.

## Intent

`TableRecord` is the one data-access layer. Today it works only on tables named
in `record.allowedTables`, and that list lives in `app_properties`. Two problems
follow.

- `PropertyRepository` reads `app_properties`. It cannot use `TableRecord`,
  because `TableRecord` needs the list, and the list needs `PropertyRepository`.
- Every new table needs a migration that edits the list.

The goal: `TableRecord` works on any table by default. The list becomes an
optional restriction, set outside the database. Then existing direct jOOQ code
moves onto `TableRecord`.

## Goals

- Unset `record.allowedTables` means every table is allowed.
- A set value restricts access. It comes from an env var or `application.yml`.
  It never comes from `app_properties`.
- Key column is found from the database, not from the list.
- `PropertyRepository`, `ModuleManager`, `ApiScriptProcessor` and `RouteRegistry`
  use `TableRecord`.

## Non-goals

- Migrating `DatabaseAttachmentStorage` and `TableApi`. They stay on jOOQ.
- Binary column support or multi-statement transactions in `TableRecord`.
- Changing business rules, scripts or the scheduler.
- Auth or sandbox hardening. The owner postponed it.

## Anti-goals

- A failure that returns wrong data. A table with an unusable key must fail
  loudly, not guess a key.
- Opening a path for SQL injection. Names keep the identifier check.

## Decisions

Decided by the owner:

- Unset means all tables.
- The setting comes from env or properties file, not `app_properties`.
- Existing jOOQ users move to `TableRecord`.

Decided by the writer. The owner should check these:

1. **Key column.** Read the table's primary key from the database on first use.
   Cache it per table. Method: `Database` metadata or `information_schema`.
2. **No usable key.** A table with no primary key, or a composite one, is
   read-only. `query`, `next` and `count` work. Every write throws a message
   naming the table. Bulk writes pick rows by key, so they cannot run without one.
3. **List format.** Keep `table:keyColumn` pairs. The key is optional. A bare
   table name means "use the primary key". A given key overrides discovery.
4. **Source.** Env var `RECORD_ALLOWED_TABLES`, mapped in `application.yml` as
   `record.allowedTables`. Read once at startup. A change needs a restart.
5. **Old row.** A new migration deletes the `record.allowedTables` row from
   `app_properties`. Applied migrations stay untouched.
6. **Exempt list.** `DatabaseAttachmentStorage` and `TableApi` join the exempt
   list in `.claude/rules/data-access.md`, each with its reason.

## Constraints

- `TableRecord` reads the setting before `app_properties` is reachable. It must
  not call `PropertiesUtil` for this value.
- Every public `TableRecord` method is a script API. New methods need docs and
  tests.
- Flyway migrations are append-only.
- Tests mirror source packages. Run `mvn spotless:apply` before commit.

## The parts

| Part | Change |
|---|---|
| `TableRecord` | Constructor stops rejecting unlisted tables. Key lookup moves to a cached discovery. |
| `Database` or new helper | Reads a table's primary key columns. |
| Startup config | Reads `record.allowedTables` from yml or env into a static holder. |
| `application.yml` | Adds `record.allowedTables: ${RECORD_ALLOWED_TABLES:}`. |
| New migration | Deletes the `app_properties` row. |
| Four callers | Rewrite reads and writes through `TableRecord`. |

## Left to the builder

- How primary keys are read and cached.
- The exact error type and text for an unusable key.
- Whether `PropertyRepository` keeps its LRU cache. It should.
- How `RouteRegistry` replaces its fingerprint SQL. If `TableRecord` lacks a
  capability, extend it as a general method, per the data-access rule.
- Order of work. Suggested: core change and tests, then one caller at a time.

## Open risks

- `PropertyRepository` runs at boot. The builder must confirm `TableRecord`
  needs nothing from `PropertiesUtil` on that path. `TableRecord(String)` uses
  `DbPropertiesLoader.getDatabase()`, which may also need checking.
- Business rules run on `TableRecord` writes. Moving writers onto it can fire
  rules that never ran before. Only reads move in this change except the four
  callers' existing writes, if any. The builder should list them.
- With no list, scripts can reach every table, including `app_properties` and
  `routes`. The owner accepts this for now.

## Docs to update in the same change

- `docs/scripting/table-records-and-rules.md`: the allow-list section.
- `docs/architecture/configuration.md`: the new setting and env var.
- `.claude/rules/data-access.md`: allow-list and exempt list.
- `CLAUDE.md`: the "db credentials only" lines about `application.yml`.
