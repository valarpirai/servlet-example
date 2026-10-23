INSERT INTO app_properties (name, value, type, description) VALUES
  ('request.maxBodySize', '1048576', 'LONG', 'Maximum JSON or script request body in bytes (1 MB)')
ON CONFLICT (name) DO NOTHING;
