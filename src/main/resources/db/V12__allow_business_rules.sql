-- Let TableRecord, and so the table browser, read and write business_rules.
UPDATE app_properties
   SET value = value || ',business_rules:id'
 WHERE name = 'record.allowedTables'
   AND value NOT LIKE '%business_rules:id%';
