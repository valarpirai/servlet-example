-- The route table read by RouteRegistry.
--   static     resource is a classpath file
--   handler    handler is a Java class name, handler_method the method to call
--   processor  processor is a Java class name
--   builtin    handler is handleHealth or handleMetrics
--   script     handler is an api_scripts row name, handler_method the function name
-- Rows with is_default = TRUE are protected by a business rule.
CREATE TABLE routes (
    id             BIGSERIAL    PRIMARY KEY,
    path           VARCHAR(500) NOT NULL,
    methods        VARCHAR(100) NOT NULL,
    type           VARCHAR(20)  NOT NULL
                   CHECK (type IN ('static', 'handler', 'processor', 'builtin', 'script')),
    handler        VARCHAR(255),
    handler_method VARCHAR(255),
    processor      VARCHAR(255),
    resource       VARCHAR(500),
    content_type   VARCHAR(100),
    description    TEXT,
    path_params    VARCHAR(255),
    sort_order     INTEGER      NOT NULL DEFAULT 1000,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_routes_order ON routes (sort_order, id);

INSERT INTO routes
    (path, methods, type, handler, handler_method, processor, resource, content_type,
     description, path_params, sort_order, is_default) VALUES
  ('/script-editor', 'GET', 'static', NULL, NULL, NULL, 'static/script-editor.html', 'text/html', 'Interactive JavaScript code editor', NULL, 10, TRUE),
  ('/data-browser', 'GET', 'static', NULL, NULL, NULL, 'static/data-browser.html', 'text/html', 'Database browser UI', NULL, 20, TRUE),
  ('/attachments', 'GET', 'static', NULL, NULL, NULL, 'static/attachments.html', 'text/html', 'Attachments UI - upload and browse files', NULL, 30, TRUE),
  ('/tables', 'GET', 'static', NULL, NULL, NULL, 'static/table-browser.html', 'text/html', 'Table browser - all tables', NULL, 40, TRUE),
  ('/{table}_list', 'GET', 'static', NULL, NULL, NULL, 'static/table-browser.html', 'text/html', 'Table browser - rows of one table', NULL, 50, TRUE),
  ('/{table}_row', 'GET', 'static', NULL, NULL, NULL, 'static/table-browser.html', 'text/html', 'Table browser - one row, all fields', NULL, 60, TRUE),
  ('/api/tables', 'GET', 'handler', 'TableApiHandler', 'handleTables', NULL, NULL, 'application/json', 'List all tables', NULL, 70, TRUE),
  ('/api/tables/{table}/rows', 'GET', 'handler', 'TableApiHandler', 'handleRows', NULL, NULL, 'application/json', 'One page of a table. Query page, size, sort and dir; long text cut', 'table', 80, TRUE),
  ('/api/tables/{table}/row', 'GET', 'handler', 'TableApiHandler', 'handleRow', NULL, NULL, 'application/json', 'One row in full, found by the id query parameter', 'table', 90, TRUE),
  ('/api/attachments', 'GET', 'handler', 'AttachmentHandler', 'handleList', NULL, NULL, 'application/json', 'List all attachments', NULL, 100, TRUE),
  ('/api/attachment/{id}/download', 'GET', 'handler', 'AttachmentHandler', 'handleDownload', NULL, NULL, NULL, 'Download attachment by ID (streaming)', 'id', 110, TRUE),
  ('/api/attachment/{id}', 'GET', 'handler', 'AttachmentHandler', 'handleMetadata', NULL, NULL, 'application/json', 'Get attachment metadata', 'id', 120, TRUE),
  ('/api/attachment/{id}', 'DELETE', 'handler', 'AttachmentHandler', 'handleDelete', NULL, NULL, 'application/json', 'Delete attachment', 'id', 130, TRUE),
  ('/api/v1/**', 'GET,POST,PUT,DELETE,PATCH', 'handler', 'ApiHandler', 'handleRequest', NULL, NULL, 'application/json', 'Scripted REST API endpoints (loads JS from the api_scripts table)', NULL, 220, TRUE),
  ('/api/upload', 'POST', 'processor', NULL, NULL, 'FileUploadProcessor', NULL, 'application/json', 'File upload (multipart/form-data)', NULL, 230, TRUE),
  ('/api/script', 'POST', 'processor', NULL, NULL, 'ScriptProcessor', NULL, 'application/json', 'JavaScript execution', NULL, 240, TRUE),
  ('/health', 'GET', 'builtin', 'handleHealth', NULL, NULL, NULL, 'application/json', 'Health check endpoint', NULL, 260, TRUE),
  ('/metrics', 'GET', 'builtin', 'handleMetrics', NULL, NULL, NULL, 'application/json', 'System metrics endpoint', NULL, 270, TRUE),
  ('/', 'GET', 'static', NULL, NULL, NULL, 'static/index.html', 'text/html', 'Beautiful home page with links to all endpoints', NULL, 280, TRUE),
  ('/api/data-browser/db-types', 'GET', 'handler', 'DataBrowserHandler', 'handleDbTypes', NULL, NULL, 'application/json', 'List available database types for the connection dropdown', NULL, 290, TRUE),
  ('/api/data-browser/driver-status', 'POST', 'handler', 'DataBrowserHandler', 'handleDriverStatus', NULL, NULL, 'application/json', 'Check JDBC driver availability', NULL, 300, TRUE),
  ('/api/data-browser/download', 'POST', 'handler', 'DataBrowserHandler', 'handleDownload', NULL, NULL, 'application/json', 'Download JDBC driver', NULL, 310, TRUE),
  ('/api/data-browser/connect', 'POST', 'handler', 'DataBrowserHandler', 'handleConnect', NULL, NULL, 'application/json', 'Connect to database', NULL, 320, TRUE),
  ('/api/data-browser/databases', 'POST', 'handler', 'DataBrowserHandler', 'handleDatabases', NULL, NULL, 'application/json', 'List actual databases available on the connected server', NULL, 330, TRUE),
  ('/api/data-browser/select-database', 'POST', 'handler', 'DataBrowserHandler', 'handleSelectDatabase', NULL, NULL, 'application/json', 'Switch the session''s active database', NULL, 340, TRUE),
  ('/api/data-browser/tables', 'POST', 'handler', 'DataBrowserHandler', 'handleTables', NULL, NULL, 'application/json', 'List database tables', NULL, 350, TRUE),
  ('/api/data-browser/query', 'POST', 'handler', 'DataBrowserHandler', 'handleQuery', NULL, NULL, 'application/json', 'Execute SQL query', NULL, 360, TRUE),
  ('/api/data-browser/disconnect', 'POST', 'handler', 'DataBrowserHandler', 'handleDisconnect', NULL, NULL, 'application/json', 'Disconnect from database', NULL, 370, TRUE),
  ('/api/data-browser/mongo/connect', 'POST', 'handler', 'MongoHandler', 'handleConnect', NULL, NULL, 'application/json', 'Connect to MongoDB via connection string', NULL, 380, TRUE),
  ('/api/data-browser/mongo/collections', 'POST', 'handler', 'MongoHandler', 'handleCollections', NULL, NULL, 'application/json', 'List collections in a Mongo database', NULL, 390, TRUE),
  ('/api/data-browser/mongo/find', 'POST', 'handler', 'MongoHandler', 'handleFind', NULL, NULL, 'application/json', 'Run a find query against a Mongo collection', NULL, 400, TRUE),
  ('/api/data-browser/mongo/disconnect', 'POST', 'handler', 'MongoHandler', 'handleDisconnect', NULL, NULL, 'application/json', 'Disconnect from MongoDB', NULL, 410, TRUE),
  ('/hello/{who}', 'GET', 'script', 'greeter', 'greet', NULL, NULL, 'application/json', 'Sample JavaScript route', 'who', 1000, FALSE),
  ('/api/tables/{table}/rows/delete', 'POST', 'handler', 'TableApiHandler', 'handleDelete', NULL, NULL, 'application/json', 'Delete rows. Body: {"ids": [key, ...]}', 'table', 93, TRUE),
  ('/api/tables/{table}/row', 'PUT', 'handler', 'TableApiHandler', 'handleUpdate', NULL, NULL, 'application/json', 'Update the row named by the id query parameter. Body: {"values": {...}}', 'table', 92, TRUE),
  ('/{table}_new', 'GET', 'static', NULL, NULL, NULL, 'static/table-browser.html', 'text/html', 'Table browser - form for a new row', NULL, 65, TRUE),
  ('/api/tables/{table}/row', 'POST', 'handler', 'TableApiHandler', 'handleCreate', NULL, NULL, 'application/json', 'Create a row. Body: {"values": {column: value}}', 'table', 91, TRUE),
  ('/api/modules/**', 'GET,POST,PUT,DELETE', 'script', 'modules-api', 'handle', NULL, NULL, 'application/json', 'Module management endpoints', NULL, 210, TRUE),
  ('/api/scheduler/stats', 'GET', 'handler', 'SchedulerHandler', 'handleStats', NULL, NULL, 'application/json', 'Job scheduler statistics: this node''s counters and job row counts by state', NULL, 275, TRUE);
