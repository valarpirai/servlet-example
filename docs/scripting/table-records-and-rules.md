# Table Records and Business Rules

`TableRecord` reads and writes rows. Business rules run JavaScript around each write.

## What it does

`TableRecord` is a small query-and-write API over one table. Scripts and Java use
the same class. Scripts create one with `new TableRecord('modules')`.

A business rule is a row in `business_rules`. It names a table, a write, and a
moment: `before` or `after`. The runner finds the active rules and executes each
in the script sandbox.

## Allowed tables

`record.allowedTables` lists the tables `TableRecord` may touch. Each entry pairs
a table with its key column.

```
modules:name,app_properties:id
```

Any other table throws `Table is not allowed`. The key column picks the row for
`update` and `deleteRecord`. Column names must match `[A-Za-z_][A-Za-z0-9_]*`.
Operators are `=`, `!=`, `>`, `<`, `>=`, `<=` and `LIKE`.

## Reading rows

```javascript
var gr = new TableRecord('modules');
gr.addQuery('name', 'LIKE', 'utils/%');
gr.orderBy('name');
gr.setLimit(10);
gr.query();
while (gr.next()) {
  console.log(gr.getValue('name'));
}
```

`getValue` returns a string, or null. `next` moves to the next row and returns
false at the end.

## Writing rows

```javascript
var gr = new TableRecord('modules');
gr.setValue('name', 'utils/new');
gr.setValue('content', 'module.exports = {};');
var key = gr.insert();      // the key, or null when a rule aborted

gr.query();
gr.next();
gr.setValue('content', 'changed');
gr.update();                // true, or false when a rule aborted
gr.deleteRecord();          // true, or false when a rule aborted
```

`update` writes only the fields that changed. If nothing changed, it skips the
write but still runs the rules.

## A full example

Create, read, update, list and delete one property. Run it in `/api/script`, or
in a module.

```javascript
var table = 'app_properties';

// Create
var created = new TableRecord(table);
created.setValue('name', 'demo.greeting');
created.setValue('value', 'hello');
created.setValue('type', 'STRING');
var id = created.insert();

// Read
var found = new TableRecord(table);
found.addQuery('name', 'demo.greeting');
found.query();
var before = found.next() ? found.getValue('value') : null;

// Update
var edit = new TableRecord(table);
edit.addQuery('name', 'demo.greeting');
edit.query();
edit.next();
edit.setValue('value', 'hello again');
var updated = edit.update();

// Read many, ordered by name
var list = new TableRecord(table);
list.addQuery('name', 'LIKE', 'demo.%');
list.orderBy('name');
list.setLimit(10);
list.query();
var names = [];
while (list.next()) {
  names.push(list.getValue('name') + '=' + list.getValue('value'));
}

// Delete
var gone = new TableRecord(table);
gone.addQuery('name', 'demo.greeting');
gone.query();
gone.next();
var deleted = gone.deleteRecord();

JSON.stringify({ id: id, before: before, updated: updated, names: names, deleted: deleted });
```

It returns `{"id":"38","before":"hello","updated":true,"names":["demo.greeting=hello again"],"deleted":true}`.

`insert` returns the key as text. A text key works in `addQuery` for a text
column. For a numeric column such as `id`, query by another column, as above.

## Writing a rule

Columns of `business_rules`:

| Column | Meaning |
|---|---|
| `collection` | Table the rule watches |
| `run_when` | `before` or `after` |
| `on_insert`, `on_update`, `on_delete` | Writes that fire the rule |
| `condition` | JavaScript expression. Empty means always |
| `script` | JavaScript to run |
| `order_no` | Lower runs first. Ties break by `id` |
| `active` | Inactive rules never run |

A rule sees three names:

- `current` is the record being written
- `previous` is the row as read. It is `null` on insert
- `TableRecord` builds more records

`current.changes('field')` tells whether a field differs from `previous`.

Example. Block empty module content:

```sql
INSERT INTO business_rules
  (name, collection, run_when, on_insert, on_update, condition, script)
VALUES
  ('No empty modules', 'modules', 'before', TRUE, TRUE,
   'current.getValue(''content'') == null || current.getValue(''content'') == ''''',
   'current.setAbortAction(true);');
```

## Aborting a write

A before rule calls `current.setAbortAction(true)`. The write does not happen,
later rules do not run, and `insert` returns null while `update` and
`deleteRecord` return false.

An after rule cannot undo the write. Aborting there has no effect.

## Rules that fire rules

A rule that writes a record fires that record's rules. `record.maxRuleDepth`
caps the nesting at 5. Going deeper throws `Business rules nest deeper than 5`.

## Errors

A rule script that throws stops the write and surfaces as an
`IllegalStateException` naming the rule. A failure in an after rule leaves the
write in place.

Rules run outside the write's database transaction. The write commits first, then
after rules run. Rules are read from the table on every write, so an edit takes
effect at once.

## Related

- [Script sandbox](./script-sandbox.md) — limits that apply to rule scripts
- [Sandbox policy](./sandbox-policy.md) — why `TableRecord` is on the allow list
- [Script modules](./script-modules.md) — the `modules` table
- [Configuration](../architecture/configuration.md) — `record.*` properties
- [Table editing](../data-browser/table-editing.md) — the table browser writes through `TableRecord`
