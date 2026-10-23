# Route Configuration

The fields of a route, one row per route in the `routes` table.

Every route needs a `path`, `methods`, and a `type`. The type decides which other
columns apply. See [table routes](./table-routes.md) for how rows are loaded and
protected.

## Fields

| Column | Applies to | Meaning |
|---|---|---|
| `path` | all | Pattern, with `{param}` or `**` |
| `methods` | all | One method, or several separated by commas |
| `type` | all | `static`, `handler`, `processor`, `builtin` or `script` |
| `description` | all | Free text, shown in validation messages |
| `content_type` | all | Response content type |
| `resource` | `static` | Classpath path to the file |
| `handler` | `handler`, `builtin`, `script` | Handler name, builtin method name, or script name |
| `handler_method` | `handler`, `script` | Java method, or JavaScript function |
| `processor` | `processor` | Processor class name |
| `path_params` | any with `{param}` | Names to extract from the path, comma separated |
| `sort_order` | all | Lower matches first |

## The five types

**`static`** streams a classpath resource. No Java involved.

```sql
INSERT INTO routes (path, methods, type, resource, content_type, description)
VALUES ('/script-editor', 'GET', 'static', 'static/script-editor.html',
        'text/html', 'Interactive JavaScript code editor');
```

**`handler`** invokes a method on a singleton. The handler writes the response
itself, so this is the only Java type that can stream.

```sql
INSERT INTO routes (path, methods, type, handler, handler_method, path_params)
VALUES ('/api/attachment/{id}/download', 'GET', 'handler',
        'AttachmentHandler', 'handleDownload', 'id');
```

**`processor`** constructs a fresh instance per request. The processor returns a
`ProcessorResponse` and the dispatcher writes it.

```sql
INSERT INTO routes (path, methods, type, processor, content_type)
VALUES ('/api/modules/**', 'GET,POST,PUT,DELETE', 'processor',
        'ModuleProcessor', 'application/json');
```

**`builtin`** calls a `RouterServlet` method directly. Only `handleHealth` and
`handleMetrics` are valid.

```sql
INSERT INTO routes (path, methods, type, handler, content_type)
VALUES ('/health', 'GET', 'builtin', 'handleHealth', 'application/json');
```

**`script`** calls a JavaScript function in an `api_scripts` row. The function
receives the request and response objects.

```sql
INSERT INTO routes (path, methods, type, handler, handler_method)
VALUES ('/hello', 'GET', 'script', 'greeter', 'greet');
```

## Path patterns

`{name}` captures exactly one segment. List each name under `path_params` or it
will not be extracted.

```
/api/users/{userId}/posts/{postId}      path_params: userId,postId
```

`**` matches all remaining segments and captures nothing. Use it for a group of
endpoints handled by one processor.

```
/api/modules/**
```

## Multiple methods

Separate the verbs with commas when one route serves several.

```
GET,PUT,DELETE
```

## Related

- [Table routes](./table-routes.md) — loading, refresh and default routes
- [Route registry](./route-registry.md) — how matching works
- [Adding a route](./adding-a-route.md) — the steps and the ordering trap
- [Configuration](../architecture/configuration.md) — other configuration
