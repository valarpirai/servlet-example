-- The table browser can create, update and delete rows. These are default routes, added like the
-- ones V8 seeded. Writes go through TableRecord, so only allow-listed tables accept them.

INSERT INTO routes
    (path, methods, type, handler, handler_method, processor, resource, content_type, description, path_params, sort_order, is_default)
SELECT * FROM (VALUES
    ('/{table}_new', 'GET', 'static', NULL, NULL, NULL, 'static/table-browser.html', 'text/html', 'Table browser - form for a new row', NULL, 65, TRUE),
    ('/api/tables/{table}/row', 'POST', 'handler', 'TableBrowserHandler', 'handleCreate', NULL, NULL, 'application/json', 'Create a row. Body: {"values": {column: value}}', 'table', 91, TRUE),
    ('/api/tables/{table}/row', 'PUT', 'handler', 'TableBrowserHandler', 'handleUpdate', NULL, NULL, 'application/json', 'Update the row named by the id query parameter. Body: {"values": {...}}', 'table', 92, TRUE),
    ('/api/tables/{table}/rows/delete', 'POST', 'handler', 'TableBrowserHandler', 'handleDelete', NULL, NULL, 'application/json', 'Delete rows. Body: {"ids": [key, ...]}', 'table', 93, TRUE)
) AS v (path, methods, type, handler, handler_method, processor, resource, content_type, description, path_params, sort_order, is_default)
WHERE NOT EXISTS (
    SELECT 1 FROM routes r WHERE r.path = v.path AND r.methods = v.methods
);
