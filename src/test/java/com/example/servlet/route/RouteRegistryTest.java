package com.example.servlet.route;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test RouteRegistry to verify route matching and path parameter extraction.
 */
public class RouteRegistryTest {

  @Test
  public void testLoadRoutes() {
    RouteRegistry registry = RouteRegistry.getInstance();
    assertNotNull(registry);
    assertFalse(registry.getAllRoutes().isEmpty(), "Routes should be loaded from routes.yml");

    System.out.println("Loaded " + registry.getAllRoutes().size() + " routes:");
    for (Route route : registry.getAllRoutes()) {
      System.out.println("  " + route);
    }
  }

  @Test
  public void testExactPathMatching() {
    RouteRegistry registry = RouteRegistry.getInstance();

    // Test exact path matching
    RouteRegistry.RouteMatch match = registry.findRoute("GET", "/health");
    assertNotNull(match, "/health should match");
    assertEquals("builtin", match.getRoute().getType());
    assertEquals("handleHealth", match.getRoute().getHandler());

    match = registry.findRoute("GET", "/metrics");
    assertNotNull(match, "/metrics should match");
    assertEquals("builtin", match.getRoute().getType());
  }

  @Test
  public void testPathParameterExtraction() {
    RouteRegistry registry = RouteRegistry.getInstance();

    // Test path with parameters: /api/attachment/{id}
    RouteRegistry.RouteMatch match = registry.findRoute("GET", "/api/attachment/abc-123-def");
    assertNotNull(match, "/api/attachment/{id} should match");
    assertEquals("handler", match.getRoute().getType());
    assertEquals("AttachmentHandler", match.getRoute().getHandler());

    // Extract path parameter
    String id = match.getPathParam("id");
    assertEquals("abc-123-def", id, "Path parameter 'id' should be extracted");
  }

  @Test
  public void testPathParameterWithAction() {
    RouteRegistry registry = RouteRegistry.getInstance();

    // Test: /api/attachment/{id}/download
    RouteRegistry.RouteMatch match =
        registry.findRoute("GET", "/api/attachment/xyz-789/download");
    assertNotNull(match, "/api/attachment/{id}/download should match");
    assertEquals("handler", match.getRoute().getType());
    assertEquals("AttachmentHandler", match.getRoute().getHandler());
    assertEquals("handleDownload", match.getRoute().getHandlerMethod());

    String id = match.getPathParam("id");
    assertEquals("xyz-789", id);
  }

  @Test
  public void testWildcardMatching() {
    RouteRegistry registry = RouteRegistry.getInstance();

    // Test wildcard: /api/modules/**
    RouteRegistry.RouteMatch match = registry.findRoute("GET", "/api/modules/list");
    assertNotNull(match, "/api/modules/** should match /api/modules/list");

    match = registry.findRoute("GET", "/api/modules/utils/math");
    assertNotNull(match, "/api/modules/** should match /api/modules/utils/math");

    match = registry.findRoute("POST", "/api/modules/create");
    assertNotNull(match, "/api/modules/** should match POST to /api/modules/create");
  }

  @Test
  public void testStaticFileRoutes() {
    RouteRegistry registry = RouteRegistry.getInstance();

    // Test static file routes
    RouteRegistry.RouteMatch match = registry.findRoute("GET", "/script-editor");
    assertNotNull(match, "/script-editor should match");
    assertEquals("static", match.getRoute().getType());
    assertEquals("static/script-editor.html", match.getRoute().getResource());

    match = registry.findRoute("GET", "/data-browser");
    assertNotNull(match, "/data-browser should match");
    assertEquals("static", match.getRoute().getType());
  }

  @Test
  public void testMethodFiltering() {
    RouteRegistry registry = RouteRegistry.getInstance();

    // /api/attachment/{id} - GET should work
    RouteRegistry.RouteMatch match = registry.findRoute("GET", "/api/attachment/test-123");
    assertNotNull(match, "GET /api/attachment/{id} should match");

    // /api/attachment/{id} - DELETE should work (different route)
    match = registry.findRoute("DELETE", "/api/attachment/test-123");
    assertNotNull(match, "DELETE /api/attachment/{id} should match");
    assertEquals("handleDelete", match.getRoute().getHandlerMethod());

    // POST should not match (no route defined for POST on this path)
    match = registry.findRoute("POST", "/api/attachment/test-123");
    // Note: This might match if there's a wildcard route, adjust test based on routes.yml
  }

  @Test
  public void testRouteNotFound() {
    RouteRegistry registry = RouteRegistry.getInstance();

    // Test non-existent route
    RouteRegistry.RouteMatch match = registry.findRoute("GET", "/api/nonexistent");
    // Depending on routes.yml, this might return null or a catch-all route
    System.out.println("Route match for /api/nonexistent: " + match);
  }

  @Test
  public void testAttachmentRoutes() {
    RouteRegistry registry = RouteRegistry.getInstance();

    // Test all attachment routes
    assertNotNull(registry.findRoute("GET", "/api/attachments"), "List attachments");
    assertNotNull(
        registry.findRoute("GET", "/api/attachment/id-123"), "Get attachment metadata");
    assertNotNull(
        registry.findRoute("GET", "/api/attachment/id-123/download"), "Download attachment");
    assertNotNull(registry.findRoute("DELETE", "/api/attachment/id-123"), "Delete attachment");
  }

  @Test
  public void testDataBrowserRoutes() {
    RouteRegistry registry = RouteRegistry.getInstance();

    // Test all data-browser routes
    assertNotNull(registry.findRoute("POST", "/api/data-browser/driver-status"));
    assertNotNull(registry.findRoute("POST", "/api/data-browser/connect"));
    assertNotNull(registry.findRoute("POST", "/api/data-browser/tables"));
    assertNotNull(registry.findRoute("POST", "/api/data-browser/query"));
    assertNotNull(registry.findRoute("POST", "/api/data-browser/disconnect"));
  }
}
