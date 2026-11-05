-- record.allowedTables now lives in application.yml (env RECORD_ALLOWED_TABLES).
-- TableRecord needs it before the database is readable, so the table no longer holds it.
DELETE FROM app_properties WHERE name = 'record.allowedTables';
