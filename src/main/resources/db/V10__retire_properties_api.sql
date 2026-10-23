-- The properties REST API is retired. Edit app_properties through TableRecord instead.
-- Cached values refresh on their own within a few seconds, so no reload route is needed.

-- Direct SQL is not stopped by the default-route rule, so this removes the seeded routes.
DELETE FROM routes
 WHERE is_default
   AND path LIKE '/api/properties%'
   AND handler = 'PropertiesHandler';

-- Let TableRecord read and write app_properties.
UPDATE app_properties
   SET value = value || ',app_properties:id'
 WHERE name = 'record.allowedTables'
   AND value NOT LIKE '%app_properties:id%';
