# API Scripting

Define an HTTP endpoint in JavaScript, with no Java changes and no rebuild.

A request to `/api/v1/{name}` runs the script in the `api_scripts` row named
`{name}`. `ApiHandler` maps the path to a row. `ApiScriptProcessor` executes it in
the same sandbox as any other script.

## Writing an endpoint

Export a function named `httpHandler`. It receives a request and a response.

```javascript
// api_scripts row: users
function httpHandler(request, response) {
  if (request.method === 'GET') {
    response.setStatus(200);
    response.setBody({
      message: 'Hello from scripted API',
      users: ['Alice', 'Bob', 'Charlie']
    });
  } else {
    response.setStatus(405);
    response.setBody({ error: 'Method not allowed' });
  }
}
```

```bash
curl http://localhost:8080/api/v1/users
```

All of GET, POST, PUT, DELETE and PATCH reach the same script. Branch on
`request.method`.

## The request object

```javascript
{
  method: "GET",
  path: "/api/v1/users",
  queryParams: { page: "1", limit: "10" },
  headers: { "Content-Type": "application/json" },
  body: { name: "Alice" }     // parsed JSON, on POST, PUT and PATCH
}
```

A JSON body is parsed before the script runs. A body that is not valid JSON
arrives unparsed rather than failing the request.

## The response object

```javascript
response.setStatus(201);
response.setHeader("X-Custom-Header", "value");

response.setBody("plain text");
response.setBody({ key: "value" });   // objects are serialized to JSON
```

Passing an object to `setBody` serializes it. Calling `JSON.stringify` first also
works, and is the same result.

## Storing a script

A script is one row. `name` is the endpoint name.

```sql
INSERT INTO api_scripts (name, content) VALUES ('hello', $$
function httpHandler(request, response) {
  response.setStatus(200);
  response.setBody({ message: 'Hello' });
}
$$);
```

An edit takes effect on the next request. Nothing is cached and nothing restarts.
A name with no row returns 404. Names are matched exactly, so `../x` finds nothing.

The [table browser](../data-browser/table-browser.md) shows the rows.

## Shared libraries

Use `import` to keep validation and helpers out of endpoint files. Libraries are
modules in the `js_modules` table, named `lib/<name>` by convention.

```javascript
// modules row: lib/validators
function validateEmail(email) {
  return email.includes('@');
}

module.exports = { validateEmail: validateEmail };
```

```javascript
// api_scripts row: register
import validators from 'lib/validators';

function httpHandler(request, response) {
  if (request.method !== 'POST') {
    response.setStatus(405);
    response.setBody({ error: 'Method not allowed' });
    return;
  }

  if (!validators.validateEmail(request.body.email)) {
    response.setStatus(400);
    response.setBody({ error: 'Invalid email format' });
    return;
  }

  response.setStatus(201);
  response.setBody({ message: 'User registered' });
}
```

Imports resolve before the script runs. A missing module fails the request and no
user code executes. Modules can import other modules.

## Security and limits

Identical to every other script path. Same `ClassShutter`, same timeout, same
memory ceiling — see [script sandbox](./script-sandbox.md).

A scripted endpoint cannot reach the file system, the network, or JDBC. Give it
capabilities through the scope, not by widening the policy.

## Error responses

With `application.environment` set to `dev`, a script error returns a stack
trace. Set it to `prod` and errors return a message only.

## Related

- [Script sandbox](./script-sandbox.md) — the limits that apply
- [Script execution](./script-execution.md) — the shared execution path
- [Script modules](./script-modules.md) — the module system
- [Table browser](../data-browser/table-browser.md) — see the stored scripts
- [Route configuration](../routing/route-configuration.md) — how /api/v1/** is routed
