-- TableBrowserHandler is now TableApiHandler. Routes name the handler class, so update them.
UPDATE routes SET handler = 'TableApiHandler' WHERE handler = 'TableBrowserHandler';
