-- A sample JavaScript route: GET /hello/{who} calls greet() in the 'greeter' script.
-- It is not a default route, so it can be edited or deleted.

INSERT INTO api_scripts (name, content) VALUES ('greeter', $script$
// Sample script route handler. The route names this script and the function below.
function greet(request, response) {
  var who = request.pathParams.who;
  response.setStatus(200);
  response.setHeader('X-Handled-By', 'script route');
  response.setBody({
    message: 'Hello, ' + who + '!',
    method: request.method,
    path: request.path
  });
}
$script$)
ON CONFLICT (name) DO NOTHING;

INSERT INTO routes
    (path, methods, type, handler, handler_method, content_type, description, path_params, sort_order)
SELECT '/hello/{who}', 'GET', 'script', 'greeter', 'greet', 'application/json',
       'Sample JavaScript route', 'who', 1000
WHERE NOT EXISTS (SELECT 1 FROM routes WHERE path = '/hello/{who}');
