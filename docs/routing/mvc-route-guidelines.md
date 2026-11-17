# MVC Guidelines for Routes

Where each part of a new route goes, so the layers stay clean.

The route checklist is in [adding a route](./adding-a-route.md). This page says what
code belongs in the handler, the service, the model and the view. The layers are
defined in [MVC layers](../architecture/layers.md). The build enforces them.

## The split

| Part | Does | Never does |
|---|---|---|
| Route row | Maps a path and method to a handler | Holds logic |
| Handler (controller) | Reads the request, calls a service, calls a view | Touches `TableRecord`, `Database`, JDBC or jOOQ |
| Service | Validates, applies rules, calls the model | Calls a controller or a view |
| Model | Reads and writes rows through `TableRecord` | Knows about HTTP |
| View | Turns data into a response body | Reads a table or calls a service |

A handler is thin. If it grows a loop over rows or a business check, move that to a
service.

## A Java route

Take `GET /api/notes/{id}`. Build it from the model outward.

### 1. Model: read through a repository

Put table access in `repository/`. Use `TableRecord`, never the jOOQ DSL. See
[data access rules](../../.claude/rules/data-access.md).

```java
public class NoteRepository {
  public Optional<Map<String, Object>> findById(String id) {
    TableRecord notes = new TableRecord("notes");
    notes.addQueryAsText("id", id);
    notes.setLimit(1);
    notes.query();
    return notes.next() ? Optional.of(row(notes)) : Optional.empty();
  }
}
```

### 2. Service: hold the rules

Put logic in a service package. Name the package in `Layers.java` when it is new. See
[layers](../architecture/layers.md).

```java
public class NoteService {
  public Optional<Map<String, Object>> get(String id) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id is required");
    }
    return repository.findById(id);
  }
}
```

### 3. View: shape the body

Put body shapes in `view/`. A view takes plain data. Use `ResponseHelper` for JSON
writers and error shapes.

```java
public final class NoteView {
  public static Map<String, Object> of(Map<String, Object> note) {
    return Map.of("id", note.get("id"), "title", note.get("title"));
  }
}
```

### 4. Controller: wire the three

Put the handler in `controller/`. Read the request. Call the service. Pass the result
to the view.

```java
public void get(HttpServletRequest request, HttpServletResponse response) throws IOException {
  String id = (String) request.getAttribute("pathParam.id");
  try {
    Optional<Map<String, Object>> note = service.get(id);
    if (note.isEmpty()) {
      ResponseHelper.sendNotFound(response, "Note not found");
      return;
    }
    ResponseHelper.sendJson(response, 200, NoteView.of(note.get()));
  } catch (IllegalArgumentException e) {
    ResponseHelper.sendBadRequest(response, e.getMessage());
  }
}
```

The handler maps exceptions to status codes. The service throws; it never writes a
response.

### 5. Register the route

Add the `routes` row and register the handler name in both `RouteRegistry` and
`RouteDispatcher`. Steps are in [adding a route](./adding-a-route.md).

Run `mvn test -pl web -am`. `ArchitectureTest` fails if a handler reaches the data
layer or a service calls a controller.

## A JavaScript route

A `script` route is the controller. It runs in the sandbox, so the same split
applies in script form.

| Part | Where it lives |
|---|---|
| Route row | `routes` table, `type = 'script'` |
| Controller | A function in an `api_scripts` row |
| Service | A module in `js_modules`, named `lib/<name>` |
| Model | `TableRecord` calls inside the service module |
| View | A small function in a module that builds the response object |

Keep the handler short. Move rules and table access into modules.

```javascript
// js_modules row: lib/note-service
function get(id) {
  var notes = new TableRecord('notes');
  notes.addQueryAsText('id', id);
  notes.setLimit(1);
  notes.query();
  return notes.next() ? { id: notes.getValue('id'), title: notes.getValue('title') } : null;
}
module.exports = { get: get };
```

```javascript
// api_scripts row: note-handlers
import notes from 'lib/note-service';

function show(request, response) {
  var note = notes.get(request.pathParams.id);
  if (note === null) {
    response.setStatus(404);
    response.setBody({ error: 'Note not found' });
    return;
  }
  response.setStatus(200);
  response.setBody(note);
}
```

```sql
INSERT INTO routes (path, methods, type, handler, handler_method, path_params, sort_order)
VALUES ('/notes/{id}', 'GET', 'script', 'note-handlers', 'show', 'id', 50);
```

Details of the request, the response and imports are in
[API scripting](../scripting/api-scripting.md) and
[table routes](./table-routes.md).

A new global script API needs a type change in the same commit. See
[TypeScript for scripts](../scripting/typescript.md).

## Choosing Java or JavaScript

Use JavaScript by default. It needs no build and no restart. Use a Java handler when
the endpoint streams or needs the servlet objects.

## Checklist

- [ ] The route row sorts above any wildcard that would match it
- [ ] The handler does no table access and holds no business rule
- [ ] Table access goes through `TableRecord`
- [ ] Body shapes live in a view, not inline in the handler
- [ ] A new package has a layer in `Layers.java`
- [ ] Java handler names are registered in `RouteRegistry` and `RouteDispatcher`
- [ ] `mvn test -pl web -am` passes, including `ArchitectureTest`

## Related

- [Adding a route](./adding-a-route.md) — the route row and registration steps
- [MVC layers](../architecture/layers.md) — the layer map and the rules
- [Table routes](./table-routes.md) — the routes table and script routes
- [API scripting](../scripting/api-scripting.md) — writing the script handler
- [Data access rules](../../.claude/rules/data-access.md) — why `TableRecord`
