-- Script storage and the rules that run around table writes.
--   api_scripts     JavaScript endpoints and job handlers, keyed by name
--   js_modules      JavaScript modules for require(), keyed by path
--   business_rules  scripts run before, after or async to a table write

CREATE FUNCTION set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END
$$;

CREATE TABLE api_scripts (
    name       VARCHAR(255) PRIMARY KEY,
    content    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE js_modules (
    name       VARCHAR(500) PRIMARY KEY,
    content    TEXT         NOT NULL,
    size_bytes INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TRIGGER js_modules_updated_at
    BEFORE UPDATE ON js_modules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE business_rules (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    collection VARCHAR(255) NOT NULL,
    run_when   VARCHAR(10)  NOT NULL CHECK (run_when IN ('before', 'after', 'async')),
    on_insert  BOOLEAN      NOT NULL DEFAULT FALSE,
    on_update  BOOLEAN      NOT NULL DEFAULT FALSE,
    on_delete  BOOLEAN      NOT NULL DEFAULT FALSE,
    condition  TEXT,
    script     TEXT         NOT NULL,
    order_no   INTEGER      NOT NULL DEFAULT 100,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_business_rules_collection ON business_rules (collection, active);

-- Scripts the app ships with.
INSERT INTO api_scripts (name, content) VALUES
  ('greeter',
   $js$
// Sample script route handler. The route names this script and the function below.
function greet(request, response) {
  var who = request.pathParams.who;
  response.setStatus(200);
  response.setHeader('X-Handled-By', 'script route');
  response.setBody({
    message: 'Hello, ' + who + '!',
    method: request.method,
    path: request.path
  });
}
$js$),
  ('hello-script',
   $js$function run(request, response) {
  response.setBody({ message: "Hello World from a JavaScript job", method: request.method });
}$js$),
  ('modules-api',
   $js$
// Module manager API. Route: /api/modules/** (GET, POST, PUT, DELETE).
// Reads and writes the js_modules table through TableRecord, so business rules apply.
//   GET    /api/modules/list         all modules
//   GET    /api/modules/{name}       one module
//   POST   /api/modules/create       body { path, content }
//   PUT    /api/modules/{name}       body { content }
//   DELETE /api/modules/{name}

var TABLE = 'js_modules';
var DEFAULT_MAX_BYTES = 1048576;

function reply(response, status, data) {
  response.setStatus(status);
  response.setBody({ status: 'success', data: data, timestamp: new Date().getTime() });
}

function fail(response, status, error, message) {
  response.setStatus(status);
  response.setBody({ error: error, status: status, timestamp: new Date().getTime(), message: message });
}

function byteLength(text) {
  return new java.lang.String(text).getBytes('UTF-8').length;
}

/** Largest module in bytes: the modules.maxFileSize property, or 1 MB. */
function maxBytes() {
  var p = new TableRecord('app_properties');
  p.addQuery('name', 'modules.maxFileSize');
  p.query();
  return p.next() ? parseInt(p.getValue('value'), 10) : DEFAULT_MAX_BYTES;
}

/** Strips slashes and the .js suffix, so /utils/math.js becomes utils/math. */
function normalize(path) {
  var name = String(path).replace(/\\/g, '/').replace(/^\/+|\/+$/g, '');
  return name.replace(/\.js$/, '');
}

/** Returns an error message for a bad module path, or null. */
function checkPath(path) {
  if (path === undefined || path === null || String(path).trim() === '') {
    return 'Module path cannot be empty';
  }
  if (/[<>:"|?*]/.test(path)) {
    return 'Module path contains invalid characters';
  }
  if (String(path).indexOf('..') >= 0) {
    return "Module path cannot contain '..'";
  }
  return null;
}

/** Returns an error message for bad content, or null. */
function checkContent(content) {
  if (content === undefined || content === null) {
    return 'Module content cannot be null';
  }
  if (byteLength(content) > maxBytes()) {
    return 'Module content exceeds maximum size of ' + maxBytes() + ' bytes';
  }
  return null;
}

function toModule(record) {
  var name = record.getValue('name');
  var content = record.getValue('content');
  return {
    name: name,
    path: name + '.js',
    content: content,
    createdAt: record.getValue('created_at'),
    updatedAt: record.getValue('updated_at'),
    size: byteLength(content)
  };
}

/** Loads one module row, or returns null. */
function find(name) {
  var record = new TableRecord(TABLE);
  record.addQuery('name', name);
  record.query();
  return record.next() ? record : null;
}

function listModules(response) {
  var list = new TableRecord(TABLE);
  list.orderBy('name');
  list.query();
  var modules = [];
  while (list.next()) {
    modules.push(toModule(list));
  }
  reply(response, 200, { modules: modules, count: modules.length });
}

function getModule(response, name) {
  var record = find(name);
  if (record === null) {
    return fail(response, 404, 'Not Found', 'Module not found: ' + name);
  }
  reply(response, 200, toModule(record));
}

function createModule(response, body) {
  if (body === null || body === undefined || Object.keys(body).length === 0) {
    return fail(response, 400, 'Bad Request', 'Empty request body');
  }
  if (body.path === undefined || body.content === undefined) {
    return fail(response, 400, 'Bad Request', 'Missing required fields: path, content');
  }
  var problem = checkPath(body.path) || checkContent(body.content);
  if (problem) {
    return fail(response, 400, 'Bad Request', problem);
  }
  var name = normalize(body.path);
  if (find(name) !== null) {
    return fail(response, 400, 'Bad Request', 'Module already exists: ' + body.path);
  }
  var record = new TableRecord(TABLE);
  record.setValue('name', name);
  record.setValue('content', body.content);
  record.setValue('size_bytes', byteLength(body.content));
  if (record.insert() === null) {
    return fail(response, 409, 'Conflict', 'A business rule stopped the create of module: ' + body.path);
  }
  response.setStatus(201);
  response.setBody({ status: 'success', data: toModule(find(name)), timestamp: new Date().getTime() });
}

function updateModule(response, name, body) {
  if (body === null || body === undefined || body.content === undefined) {
    return fail(response, 400, 'Bad Request', 'Missing required field: content');
  }
  var problem = checkContent(body.content);
  if (problem) {
    return fail(response, 400, 'Bad Request', problem);
  }
  var record = find(name);
  if (record === null) {
    return fail(response, 404, 'Not Found', 'Module not found: ' + name);
  }
  record.setValue('content', body.content);
  record.setValue('size_bytes', byteLength(body.content));
  if (!record.update()) {
    return fail(response, 409, 'Conflict', 'A business rule stopped the update of module: ' + name);
  }
  reply(response, 200, toModule(find(name)));
}

function deleteModule(response, name) {
  var record = find(name);
  if (record === null) {
    return fail(response, 404, 'Not Found', 'Module not found: ' + name);
  }
  if (!record.deleteRecord()) {
    return fail(response, 409, 'Conflict', 'A business rule stopped the delete of module: ' + name);
  }
  reply(response, 200, { path: name, message: 'Module deleted successfully' });
}

/** Route entry point. The operation is whatever follows /api/modules/. */
function handle(request, response) {
  var operation = String(request.path).replace(/^\/api\/modules\/?/, '');
  if (operation === '') {
    operation = 'list';
  }
  try {
    switch (String(request.method)) {
      case 'GET':
        return operation === 'list' ? listModules(response) : getModule(response, normalize(operation));
      case 'POST':
        return createModule(response, request.body);
      case 'PUT':
        return updateModule(response, normalize(operation), request.body);
      case 'DELETE':
        return deleteModule(response, normalize(operation));
      default:
        return fail(response, 405, 'Method Not Allowed', 'Method not supported');
    }
  } catch (e) {
    return fail(response, 500, 'Internal Server Error', 'Module request failed: ' + e.message);
  }
}
$js$);


-- Rules the app ships with.
--   Protect default routes     stops edits to the routes the app needs to run
--   Reload scheduler settings  applies a scheduler.* setting change to the live scheduler
INSERT INTO business_rules
    (name, collection, run_when, on_insert, on_update, on_delete, condition, script, order_no) VALUES
  ('Protect default routes', 'routes', 'before', TRUE, TRUE, TRUE, '(previous !== null && previous.getValue(''is_default'') == ''true'') || current.getValue(''is_default'') == ''true''',
   $js$current.setAbortAction(true);$js$, 10),
  ('Reload scheduler settings', 'app_properties', 'async', TRUE, TRUE, FALSE, 'String(current.getValue(''name'')).indexOf(''scheduler.'') === 0',
   $js$if (typeof Scheduler !== 'undefined') { Scheduler.reload(); }$js$, 100);
