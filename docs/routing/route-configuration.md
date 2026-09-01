# Route Configuration

The `routes.yml` format, one entry per route.

Every route needs a `path`, a `method`, and a `type`. The type decides which
other fields apply.

## Fields

| Field | Applies to | Meaning |
|---|---|---|
| `path` | all | Pattern, with `{param}` or `**` |
| `method` | all | One method, or a list |
| `type` | all | `static`, `handler`, `processor`, or `builtin` |
| `description` | all | Free text, shown in validation errors |
| `contentType` | all | Response content type |
| `resource` | `static` | Classpath path to the file |
| `handler` | `handler`, `builtin` | Handler name, or builtin method name |
| `handlerMethod` | `handler` | Method to invoke on the singleton |
| `processor` | `processor` | Processor class name |
| `pathParams` | any with `{param}` | Names to extract from the path |

## The four types

**`static`** streams a classpath resource. No Java involved.

```yaml
- path: /script-editor
  method: GET
  type: static
  resource: static/script-editor.html
  contentType: text/html
  description: Interactive JavaScript code editor
```

**`handler`** invokes a method on a singleton. The handler writes the response
itself, so this is the only type that can stream.

```yaml
- path: /api/attachment/{id}/download
  method: GET
  type: handler
  handler: AttachmentHandler
  handlerMethod: handleDownload
  pathParams:
    - id
  description: Download attachment by ID
```

**`processor`** constructs a fresh instance per request. The processor returns a
`ProcessorResponse` and the dispatcher writes it.

```yaml
- path: /api/modules/**
  method: [GET, POST, PUT, DELETE]
  type: processor
  processor: ModuleProcessor
  contentType: application/json
  description: Module management endpoints
```

**`builtin`** calls a `RouterServlet` method directly. Only `handleHealth` and
`handleMetrics` are valid.

```yaml
- path: /health
  method: GET
  type: builtin
  handler: handleHealth
  contentType: application/json
  description: Health check endpoint
```

## Path patterns

`{name}` captures exactly one segment. List each name under `pathParams` or it
will not be extracted.

```yaml
- path: /api/users/{userId}/posts/{postId}
  pathParams:
    - userId
    - postId
```

`**` matches all remaining segments and captures nothing. Use it for a group of
endpoints handled by one processor.

```yaml
- path: /api/modules/**
```

## Multiple methods

Give `method` a list when one route serves several verbs.

```yaml
- path: /api/properties/{key}
  method: [GET, PUT, DELETE]
```

## Related

- [Route registry](./route-registry.md) — how matching works
- [Adding a route](./adding-a-route.md) — the steps and the ordering trap
- [Configuration](../architecture/configuration.md) — other resource files
