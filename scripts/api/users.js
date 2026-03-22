// Example API handler for /api/v1/users endpoint
// Demonstrates request/response handling and require() usage

var utils = require('../lib/utils.js');

// In-memory user store (for demonstration)
var users = [
  { id: 1, name: 'Alice', email: 'alice@example.com' },
  { id: 2, name: 'Bob', email: 'bob@example.com' }
];
var nextId = 3;

/**
 * HTTP handler function - entry point for all requests
 */
function httpHandler(request, response) {
  var method = request.method;

  if (method === 'GET') {
    handleGet(request, response);
  } else if (method === 'POST') {
    handlePost(request, response);
  } else if (method === 'PUT') {
    handlePut(request, response);
  } else if (method === 'DELETE') {
    handleDelete(request, response);
  } else {
    response.setStatus(405);
    response.setBody(utils.jsonResponse({
      error: 'Method Not Allowed',
      message: 'Supported methods: GET, POST, PUT, DELETE'
    }));
  }
}

/**
 * Handle GET request - list all users or get specific user
 */
function handleGet(request, response) {
  var queryParams = request.queryParams;
  var userId = queryParams.id;

  if (userId) {
    // Get specific user
    var user = findUserById(parseInt(userId));
    if (user) {
      response.setStatus(200);
      response.setBody(utils.jsonResponse({ user: user }));
    } else {
      response.setStatus(404);
      response.setBody(utils.jsonResponse({ error: 'User not found' }));
    }
  } else {
    // List all users
    response.setStatus(200);
    response.setBody(utils.jsonResponse({
      users: users,
      count: users.length
    }));
  }
}

/**
 * Handle POST request - create new user
 */
function handlePost(request, response) {
  var body = request.body;

  // Validate required fields
  var missing = utils.validateRequired(body, ['name', 'email']);
  if (missing.length > 0) {
    response.setStatus(400);
    response.setBody(utils.jsonResponse({
      error: 'Bad Request',
      message: 'Missing required fields: ' + missing.join(', ')
    }));
    return;
  }

  // Create new user
  var newUser = {
    id: nextId++,
    name: body.name,
    email: body.email
  };
  users.push(newUser);

  response.setStatus(201);
  response.setHeader('Location', '/api/v1/users?id=' + newUser.id);
  response.setBody(utils.jsonResponse({
    message: 'User created successfully',
    user: newUser
  }));
}

/**
 * Handle PUT request - update existing user
 */
function handlePut(request, response) {
  var queryParams = request.queryParams;
  var userId = queryParams.id;
  var body = request.body;

  if (!userId) {
    response.setStatus(400);
    response.setBody(utils.jsonResponse({
      error: 'Bad Request',
      message: 'User ID is required'
    }));
    return;
  }

  var user = findUserById(parseInt(userId));
  if (!user) {
    response.setStatus(404);
    response.setBody(utils.jsonResponse({ error: 'User not found' }));
    return;
  }

  // Update user fields
  if (body.name) user.name = body.name;
  if (body.email) user.email = body.email;

  response.setStatus(200);
  response.setBody(utils.jsonResponse({
    message: 'User updated successfully',
    user: user
  }));
}

/**
 * Handle DELETE request - delete user
 */
function handleDelete(request, response) {
  var queryParams = request.queryParams;
  var userId = queryParams.id;

  if (!userId) {
    response.setStatus(400);
    response.setBody(utils.jsonResponse({
      error: 'Bad Request',
      message: 'User ID is required'
    }));
    return;
  }

  var index = findUserIndexById(parseInt(userId));
  if (index === -1) {
    response.setStatus(404);
    response.setBody(utils.jsonResponse({ error: 'User not found' }));
    return;
  }

  users.splice(index, 1);

  response.setStatus(200);
  response.setBody(utils.jsonResponse({
    message: 'User deleted successfully'
  }));
}

/**
 * Helper: Find user by ID
 */
function findUserById(id) {
  for (var i = 0; i < users.length; i++) {
    if (users[i].id === id) {
      return users[i];
    }
  }
  return null;
}

/**
 * Helper: Find user index by ID
 */
function findUserIndexById(id) {
  for (var i = 0; i < users.length; i++) {
    if (users[i].id === id) {
      return i;
    }
  }
  return -1;
}
