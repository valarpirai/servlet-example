-- The modules table becomes js_modules. Keep the allow-list and the business rules in step.

ALTER TABLE IF EXISTS modules RENAME TO js_modules;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'modules_pkey') THEN
        ALTER TABLE js_modules RENAME CONSTRAINT modules_pkey TO js_modules_pkey;
    END IF;
END
$$;

UPDATE app_properties
   SET value = regexp_replace(value, '(^|,)modules:', '\1js_modules:')
 WHERE name = 'record.allowedTables';

UPDATE business_rules SET collection = 'js_modules' WHERE collection = 'modules';
