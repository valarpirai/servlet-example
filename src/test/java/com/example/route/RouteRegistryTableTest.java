package com.example.route;

import static org.junit.jupiter.api.Assertions.*;

import com.example.model.Route;
import org.junit.jupiter.api.Test;

class RouteRegistryTableTest {

  private final FakeRoutesDb db = new FakeRoutesDb();

  /** A registry that checks the table on every lookup. */
  private RouteRegistry registry() {
    return new RouteRegistry(db, 0);
  }

  // --- loading ---

  @Test
  void loadsRoutesFromTheTable() {
    db.route("/hello/{name}", "GET", "script", "hello-script", "greet");

    RouteRegistry.RouteMatch match = registry().findRoute("GET", "/hello/Ann");

    assertNotNull(match);
    assertEquals("script", match.getRoute().getType());
    assertEquals("hello-script", match.getRoute().getHandler());
    assertEquals("greet", match.getRoute().getHandlerMethod());
  }

  @Test
  void doesNotMixInTheBuiltInRoutes() {
    db.route("/only-in-table", "GET", "script", "s", "f");

    RouteRegistry registry = registry();

    assertNotNull(registry.findRoute("GET", "/only-in-table"));
    assertNull(
        registry.findRoute("GET", "/health"), "/health is a built-in route, not in the table");
  }

  @Test
  void firstRowWins() {
    db.route("/same", "GET", "script", "first", "f");
    db.route("/same", "GET", "script", "second", "f");

    assertEquals("first", registry().findRoute("GET", "/same").getRoute().getHandler());
  }

  @Test
  void ordersRowsInSql() {
    db.route("/one", "GET", "script", "s", "f");
    registry();

    String select = db.sql.stream().filter(s -> !s.contains("md5(")).findFirst().orElseThrow();
    assertTrue(select.contains("order by"), select);
    assertTrue(select.contains("sort_order"), select);
    assertTrue(select.contains("active"), select);
  }

  @Test
  void carriesIdAndDefaultFlag() {
    long customId = db.route("/custom", "GET", "script", "s", "f");
    db.defaultRoute("/default", "GET", "script", "s", "f");
    RouteRegistry registry = registry();

    Route custom = registry.findRoute("GET", "/custom").getRoute();
    Route builtIn = registry.findRoute("GET", "/default").getRoute();

    assertEquals(customId, custom.getId());
    assertFalse(custom.isDefaultRoute());
    assertTrue(builtIn.isDefaultRoute());
  }

  @Test
  void splitsMethodsAndIgnoresCase() {
    db.route("/multi", "get, post", "script", "s", "f");
    RouteRegistry registry = registry();

    assertNotNull(registry.findRoute("GET", "/multi"));
    assertNotNull(registry.findRoute("POST", "/multi"));
    assertNull(registry.findRoute("DELETE", "/multi"));
  }

  @Test
  void scriptRouteWithoutFunctionNameUsesHttpHandler() {
    db.route("/s", "GET", "script", "my-script", null);

    assertEquals("httpHandler", registry().findRoute("GET", "/s").getRoute().getHandlerMethod());
  }

  // --- bad rows ---

  @Test
  void skipsAnInvalidRowAndKeepsTheRest() {
    db.route("/bad", "GET", "handler", "NoSuchHandler", "handle");
    db.route("/good", "GET", "script", "s", "f");
    RouteRegistry registry = registry();

    assertNull(registry.findRoute("GET", "/bad"));
    assertNotNull(registry.findRoute("GET", "/good"));
  }

  @Test
  void skipsARowWithAnUnknownType() {
    db.route("/weird", "GET", "teleport", "s", "f");
    db.route("/good", "GET", "script", "s", "f");

    assertNull(registry().findRoute("GET", "/weird"));
  }

  @Test
  void skipsAScriptRouteWithoutAScriptName() {
    db.route("/noscript", "GET", "script", null, "f");
    db.route("/good", "GET", "script", "s", "f");
    RouteRegistry registry = registry();

    assertNull(registry.findRoute("GET", "/noscript"));
    assertNotNull(registry.findRoute("GET", "/good"));
  }

  // --- startup needs the table ---

  @Test
  void failsToStartWhenTheTableIsEmpty() {
    assertThrows(IllegalStateException.class, this::registry);
  }

  @Test
  void failsToStartWhenEveryRowIsInvalid() {
    db.route("/bad", "GET", "handler", "NoSuchHandler", "handle");

    assertThrows(IllegalStateException.class, this::registry);
  }

  @Test
  void failsToStartWhenTheDatabaseIsDown() {
    db.failing = true;

    assertThrows(IllegalStateException.class, this::registry);
  }

  // --- refresh ---

  @Test
  void picksUpAnAddedRoute() {
    db.route("/one", "GET", "script", "s", "f");
    RouteRegistry registry = registry();
    assertNull(registry.findRoute("GET", "/two"));

    db.route("/two", "GET", "script", "s", "f");

    assertNotNull(registry.findRoute("GET", "/two"));
  }

  @Test
  void picksUpAnEditedRoute() {
    long id = db.route("/one", "GET", "script", "before", "f");
    RouteRegistry registry = registry();

    db.setHandler(id, "after");

    assertEquals("after", registry.findRoute("GET", "/one").getRoute().getHandler());
  }

  @Test
  void dropsADeletedRoute() {
    long id = db.route("/gone", "GET", "script", "s", "f");
    db.route("/stays", "GET", "script", "s", "f");
    RouteRegistry registry = registry();

    db.remove(id);

    assertNull(registry.findRoute("GET", "/gone"));
    assertNotNull(registry.findRoute("GET", "/stays"));
  }

  @Test
  void readsRowsOnlyWhenTheTableChanged() {
    db.route("/one", "GET", "script", "s", "f");
    RouteRegistry registry = registry();
    int readsAfterStart = db.tableReads;

    registry.findRoute("GET", "/one");
    registry.findRoute("GET", "/one");

    assertEquals(readsAfterStart, db.tableReads);
  }

  @Test
  void keepsCurrentRoutesWhenARefreshFails() {
    db.route("/one", "GET", "script", "s", "f");
    RouteRegistry registry = registry();

    db.failing = true;

    assertNotNull(registry.findRoute("GET", "/one"));
  }

  @Test
  void waitsBetweenChecks() {
    db.route("/one", "GET", "script", "s", "f");
    RouteRegistry slow = new RouteRegistry(db, 60_000);
    int statementsAfterStart = db.sql.size();

    slow.findRoute("GET", "/one");

    assertEquals(statementsAfterStart, db.sql.size(), "no check inside the interval");
  }
}
