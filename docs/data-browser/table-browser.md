# Table Browser

A web view of this app's own database tables. Allow-listed tables can be edited. See
[table editing](./table-editing.md).

## What it does

Four pages show the tables of the `public` schema. One HTML file,
`static/table-browser.html`, serves all of them. It reads the URL path to pick a view.

| URL | Shows |
|---|---|
| `/tables` | Every table, with its column count |
| `/<table>_list` | One page of rows from a table |
| `/<table>_row?id=<key>` | One row, every field in full, as a form |
| `/<table>_new` | A form for a new row |

Click a table to open its list. Click a row to open the record.

The data browser at `/data-browser` connects to other databases. This one reads the
application database only.

## The list page

The list shows the first 8 columns. A button reveals the rest.

Text is cut in the cell with an ellipsis. Hover a cell to read it. The server also
cuts text at 200 characters, so a large module or attachment never floods the page.
Binary values show as `<binary N bytes>`.

Rows with a key column are clickable. A table without a single-column key has no
record page, and the list says so.

### Paging

The page shows 25 rows by default. The size menu offers 10, 25, 50 and 100. The
server never returns more than 100 rows.

A page past the end returns no rows and the total, not an error.

### Sorting

Click a column header. The first click sorts ascending, the second descending, the
third restores the default order, which is the key column.

Sorting adds the key column as a tie-breaker. A row never appears on two pages.

Columns of type `json`, `xml` and `bytea` have no useful order. Their headers do not
sort, and the API rejects them.

### The URL holds the state

```
/app_properties_list?page=2&size=10&sort=name&dir=desc
```

Reload or share the link and the page looks the same. The column toggle is not in
the URL.

## The record page

`/<table>_row?id=<key>` shows each column with its type and full value, in a form. The `id`
value is compared as text against the table's key column. Only single-column keys
work.

## API

The read endpoints are `GET` and return JSON. `TableApiHandler` serves them.
The write endpoints are in [table editing](./table-editing.md).

| Endpoint | Returns |
|---|---|
| `/api/tables` | `{tables: [{name, columns}]}` |
| `/api/tables/{table}/rows` | Columns, key, `writable`, total, page, size, sort and rows |
| `/api/tables/{table}/row?id=` | Columns, key, `writable` and one row |

Query parameters for `rows`:

| Parameter | Meaning |
|---|---|
| `page` | Page number from 1. Lower values read page 1 |
| `size` | Rows per page. Default 25, most 100 |
| `sort` | Column to sort by |
| `dir` | `desc` for descending. Anything else is ascending |

A malformed `page` or `size` falls back to its default. A `sort` that is not a
sortable column returns 400. An unknown table or row returns 404.

## How it stays safe

`TableApi` checks the table name against `information_schema` before it uses
it, then quotes it. The sort column must match a real, sortable column of that
table. Names that match nothing never reach a query.

The page builds its DOM with text nodes. Table data never becomes HTML.

Like the rest of the app, the browser has no login. It lists every table, including
`app_properties`, and can write the allow-listed ones. Keep it off any network you do
not trust.

## Where the code lives

- `tableapi/TableApi.java` holds the queries
- `handler/TableApiHandler.java` turns them into HTTP
- `static/table-browser.html` is the UI
- `tableapi/TableEditor.java` holds the writes
- the `routes` table registers four static routes and six API routes

## Related

- [Table editing](./table-editing.md) — create, update and delete
- [Data browser](./data-browser.md) — the browser for other databases
- [Adding a route](../routing/adding-a-route.md) — how the routes were registered
- [Route registry](../routing/route-registry.md) — why handler names appear twice
- [Package map](../architecture/package-map.md) — where `browser/` sits
