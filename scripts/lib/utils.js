// Shared utility functions for API handlers

/**
 * Create a JSON response
 */
function jsonResponse(data) {
  return JSON.stringify(data);
}

/**
 * Parse query parameters into an object
 */
function parseQueryParams(queryString) {
  var params = {};
  if (!queryString) return params;

  var pairs = queryString.split('&');
  for (var i = 0; i < pairs.length; i++) {
    var pair = pairs[i].split('=');
    if (pair.length === 2) {
      params[decodeURIComponent(pair[0])] = decodeURIComponent(pair[1]);
    }
  }
  return params;
}

/**
 * Validate required fields in request body
 */
function validateRequired(body, fields) {
  var missing = [];
  for (var i = 0; i < fields.length; i++) {
    if (!body || !body[fields[i]]) {
      missing.push(fields[i]);
    }
  }
  return missing;
}

/**
 * Create error response
 */
function errorResponse(status, message) {
  return {
    status: status,
    error: message
  };
}

// Export functions
module.exports = {
  jsonResponse: jsonResponse,
  parseQueryParams: parseQueryParams,
  validateRequired: validateRequired,
  errorResponse: errorResponse
};
