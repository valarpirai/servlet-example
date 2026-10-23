package com.example.route;

/** A registry holding the routes that the routing tests exercise, as the routes table would. */
final class TestRoutes {

  private TestRoutes() {}

  /** A registry over an in-memory routes table. It never re-reads the table. */
  static RouteRegistry registry() {
    FakeRoutesDb db = new FakeRoutesDb();
    long editor = db.defaultRoute("/script-editor", "GET", "static", null, null);
    db.setResource(editor, "static/script-editor.html");
    db.setContentType(editor, "text/html");
    long browser = db.defaultRoute("/data-browser", "GET", "static", null, null);
    db.setResource(browser, "static/data-browser.html");
    db.setContentType(browser, "text/html");
    db.defaultRoute("/api/attachments", "GET", "handler", "AttachmentHandler", "handleList");
    String[][] byId = {
      {"/api/attachment/{id}/download", "GET", "handleDownload"},
      {"/api/attachment/{id}", "GET", "handleMetadata"},
      {"/api/attachment/{id}", "DELETE", "handleDelete"}
    };
    for (String[] r : byId) {
      db.setPathParams(db.defaultRoute(r[0], r[1], "handler", "AttachmentHandler", r[2]), "id");
    }
    long modules =
        db.defaultRoute("/api/modules/**", "GET,POST,PUT,DELETE", "processor", null, null);
    db.setProcessor(modules, "ModuleProcessor");
    db.defaultRoute("/health", "GET", "builtin", "handleHealth", null);
    db.defaultRoute("/metrics", "GET", "builtin", "handleMetrics", null);
    String[][] browserApi = {
      {"driver-status", "handleDriverStatus"},
      {"connect", "handleConnect"},
      {"tables", "handleTables"},
      {"query", "handleQuery"},
      {"disconnect", "handleDisconnect"}
    };
    for (String[] r : browserApi) {
      db.defaultRoute("/api/data-browser/" + r[0], "POST", "handler", "DataBrowserHandler", r[1]);
    }
    for (String prefix : new String[] {"/api/", "/health", "/metrics"}) {
      db.setJsonContentTypeFor(prefix);
    }
    return new RouteRegistry(db, Long.MAX_VALUE);
  }
}
