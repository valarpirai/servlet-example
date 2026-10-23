# Table Routes

Routes live in the `routes` table. Edit a row and the route changes.

## What it does

`RouteRegistry` reads active rows from `routes`, ordered by `sort_order` then `id`.
It checks the table every 5 seconds. When any row changed, it reloads. No restart.

Each node polls on its own, so a change reaches every node within seconds.

## Columns

| Column | Meaning |
|---|---|
| `path` | Pattern, with `{param}` or `**` |
| `methods` | Comma separated, such as `GET,POST` |
| `type` | `static`, `handler`, `processor`, `builtin` or `script` |
| `handler` | Java handler name, builtin method, or `api_scripts` row name |
| `handler_method` | Java method, or JavaScript function |
| `processor` | Processor name, for `processor` routes |
| `resource` | Classpath file, for `static` routes |
| `content_type` | Response content type |
| `path_params` | Comma separated names for the `{param}` parts |
| `sort_order` | Lower matches first. First match wins |
| `is_default` | True for default routes. See below |
| `active` | False hides the route |

[Route configuration](./route-configuration.md) describes each type.

## Java handlers and JavaScript handlers

A Java route names a handler class and a method.

```sql
INSERT INTO routes (path, methods, type, handler, handler_method, content_type)
VALUES ('/files', 'GET', 'handler', 'AttachmentHandler', 'handleList', 'application/json');
```

A JavaScript route names an `api_scripts` row and a function in it.

```sql
INSERT INTO routes (path, methods, type, handler, handler_method, path_params)
VALUES ('/users/{id}', 'GET', 'script', 'user-handlers', 'showUser', 'id');
```

Both kinds receive the request and response. In a Java method they are the servlet
objects. In JavaScript they are the same objects an `/api/v1/{name}` script gets.

```javascript
// api_scripts row: user-handlers
function showUser(request, response) {
  response.setStatus(200);
  response.setBody({ id: request.pathParams.id, method: request.method });
}
```

`request` holds `method`, `path`, `pathParams`, `queryParams`, `headers` and `body`.
Leave `handler_method` empty and the function name is `httpHandler`.

A script route fails with 500 when the script has no function of that name. The
`/api/v1/{name}` route is more lenient. It runs a script that has no `httpHandler`.

A sample ships with the app. `GET /hello/{who}` calls `greet` in the `greeter` script
and returns the name from the path. It is not a default route, so edit or delete it
freely.

```bash
curl http://localhost:8080/hello/Ann
# {"message":"Hello, Ann!","method":"GET","path":"/hello/Ann"}
```

A script route can `import` modules like any other script. See
[script modules](../scripting/script-modules.md).

## Default routes

Every route that ships with the app is a default route, marked `is_default`. A
default route cannot be edited or deleted. This includes turning it off.

A business rule, "Protect default routes", enforces this. It runs before every
insert, update and delete on `routes`, and aborts when:

- the row being changed or deleted is a default route
- the new values say `is_default = true`

So no one can change a default route, or invent a new one.

The rule fires for writes made through `TableRecord`. Direct SQL skips business
rules. Manage routes through `TableRecord`, as described next.

## Changing routes

`routes` is on the `record.allowedTables` list, so scripts can write it.

```javascript
var route = new TableRecord('routes');
route.setValue('path', '/hello/{who}');
route.setValue('methods', 'GET');
route.setValue('type', 'script');
route.setValue('handler', 'greeter');
route.setValue('handler_method', 'greet');
route.setValue('path_params', 'who');
route.setValue('sort_order', 500);
route.insert();      // the new id, or null when a rule aborted
```

`update` and `deleteRecord` return false when the rule blocks them.

A new route goes live within 5 seconds.

Put a route below the defaults, with a large `sort_order`, unless it must win
over a default path. Default routes use 10, 20, 30 and so on.

## A route that shipped with code

A new Java handler needs a default route. Add it in a Flyway migration with
`is_default` set to true. See [adding a route](./adding-a-route.md).

## Bad rows

The registry checks each row as it loads. A row that fails is skipped and logged.
The other routes load. A row fails when:

- `type` is unknown
- a `handler` route names a class or method that does not exist
- a `script` route has no `handler` or `handler_method`
- the path does not compile

## When the table is not available

The registry needs the table. Startup stops when:

- there is no database, or the table cannot be read
- the table has no usable routes

If a refresh fails, the registry keeps the routes it has.

## Related

- [Route registry](./route-registry.md) — matching and validation
- [Route configuration](./route-configuration.md) — the route types
- [Adding a route](./adding-a-route.md) — the steps for a Java handler
- [Table records and business rules](../scripting/table-records-and-rules.md) — `TableRecord` and rules
- [API scripting](../scripting/api-scripting.md) — writing the script handler
