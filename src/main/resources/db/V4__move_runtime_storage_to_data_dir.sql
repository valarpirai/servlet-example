-- Runtime storage roots moved under a single data/ directory.
-- Only rows still holding the previous default are updated, so a deployment
-- that customized either path keeps its own value.

UPDATE app_properties
   SET value = 'data/modules'
 WHERE name = 'modules.directory'
   AND value = 'modules';

UPDATE app_properties
   SET value = 'data/attachments'
 WHERE name = 'storage.filesystem.path'
   AND value = 'attachments';
