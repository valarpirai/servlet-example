// Simple hello world API endpoint for /api/v1/hello
// Demonstrates basic request/response handling

/**
 * HTTP handler function
 */
function httpHandler(request, response) {
  var queryParams = request.queryParams;
  var name = queryParams.name || 'World';

  var responseData = {
    message: 'Hello, ' + name + '!',
    timestamp: new Date().toISOString(),
    method: request.method,
    path: request.path
  };

  response.setStatus(200);
  response.setHeader('X-Custom-Header', 'API-Example');
  response.setBody(JSON.stringify(responseData));
}
