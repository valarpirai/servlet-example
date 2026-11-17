-- Two Java processors become data. The module manager API is now a JavaScript route
-- (api_scripts row modules-api) and the template renderer is removed.
--
-- A trigger keeps js_modules.updated_at current, since the script route and the table
-- browser write through TableRecord and do not set it.

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $fn$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END
$fn$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS js_modules_updated_at ON js_modules;
CREATE TRIGGER js_modules_updated_at BEFORE UPDATE ON js_modules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO api_scripts (name, content) VALUES ('modules-api', $script$
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
$script$)
ON CONFLICT (name) DO NOTHING;

DELETE FROM routes WHERE path IN ('/api/modules/**', '/api/render') AND type = 'processor';

INSERT INTO routes
    (path, methods, type, handler, handler_method, content_type, description, sort_order, is_default)
SELECT '/api/modules/**', 'GET,POST,PUT,DELETE', 'script', 'modules-api', 'handle',
       'application/json', 'Module management endpoints', 210, TRUE
WHERE NOT EXISTS (SELECT 1 FROM routes WHERE path = '/api/modules/**');
