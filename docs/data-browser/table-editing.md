# Table Editing

The table browser creates, updates and deletes rows in allow-listed tables.

## What it does

Every write goes through `TableRecord`. The allow-list in `record.allowedTables`
decides which tables accept writes. Business rules run around each write, both
`before` and `after` rules. A `before` rule can stop the write.

A table is writable when the allow-list names the same key column the database uses.
The list and row APIs report this as `writable`. Other tables stay read-only. The
pages show no buttons for them, and the write APIs answer 403.

[Table records and rules](../scripting/table-records-and-rules.md) covers the
allow-list and rules.

## The pages

| Page | Does |
|---|---|
| `/<table>_list` | Tick rows, open the Actions menu, choose Delete. A `+ New` button opens the form |
| `/<table>_new` | A form with every column. Blank fields take the column default |
| `/<table>_row?id=<key>` | The same form, filled in. Save, Delete and Back buttons |

The Actions menu sits at the top of the list. It is disabled until a row is ticked.
Delete is its only action. Ticks survive paging. The page asks before it deletes.

Long text fields open as text areas sized to their content, from 4 to 30 rows. Longer
text scrolls inside the box. The area grows as you type.

The key column is locked on the edit page. Save sends only the fields you changed.
A rule that stops a write shows its message under the form.

Columns of types the server cannot write, such as `json`, `uuid` and `bytea`, show
but stay locked.

## Types

Form values arrive as text. `TableEditor` converts each one to its column type.

| Column type | Accepts |
|---|---|
| `smallint`, `integer`, `bigint` | Whole numbers |
| `numeric`, `real`, `double precision` | Decimal numbers |
| `boolean` | `true`, `false`, `t`, `f`, `1`, `0` |
| `timestamp with time zone` | ISO 8601 with an offset, `2026-01-02T03:04:05Z` |
| `timestamp without time zone` | ISO 8601 without an offset |
| `date` | `2026-01-02` |
| `text`, `character varying`, `character` | Any text, blank included |

Blank text in any other column means null. On create it means "leave it out".
A value that does not fit its type returns 400 with the column name.

## API

All bodies and replies are JSON.

| Endpoint | Body | Replies |
|---|---|---|
| `POST /api/tables/{table}/row` | `{"values": {column: value}}` | 201 `{key}` |
| `PUT /api/tables/{table}/row?id=` | `{"values": {column: value}}` | 200 `{updated: true}`, 404 when the row is missing |
| `POST /api/tables/{table}/rows/delete` | `{"ids": [key, ...]}` | 200 `{deleted, failed: [{id, reason}]}` |

Other statuses:

| Status | Meaning |
|---|---|
| 400 | Bad value, unknown column or table, key change, empty `ids`, or a database error |
| 403 | The table is not on the allow-list |
| 409 | A `before` rule stopped a create or update |

Delete handles each id on its own. A row a rule blocks, or that is missing, lands in
`failed`. The others are still deleted.

## Where the code lives

- `tableapi/TableEditor.java` converts values and calls `TableRecord`
- `handler/TableApiHandler.java` serves the three write endpoints
- `static/table-browser.html` holds the form and the list actions
- migration `V11__table_browser_writes.sql` registers the routes

## Related

- [Table browser](./table-browser.md) — the read side
- [Table records and rules](../scripting/table-records-and-rules.md) — `TableRecord` and business rules
- [Table routes](../routing/table-routes.md) — the "Protect default routes" rule blocks route edits here too
