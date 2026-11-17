package com.example.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.config.DbPropertiesLoader;
import com.example.model.Route;
import com.example.repository.RouteRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lives beside {@link FakeRoutesDb}, which is package-private. */
class RouteRepositoryTest {

  private final FakeRoutesDb db = new FakeRoutesDb();

  @Test
  void fingerprintIsStableForAnUnchangedTable() throws Exception {
    db.route("/a", "GET", "script", "s", "f");
    RouteRepository repository = new RouteRepository(db);

    assertEquals(repository.fingerprint(), repository.fingerprint());
  }

  @Test
  void fingerprintChangesWhenAnyColumnChanges() throws Exception {
    long id = db.route("/a", "GET", "script", "s", "f");
    RouteRepository repository = new RouteRepository(db);
    String before = repository.fingerprint();

    db.setHandler(id, "other");

    assertNotEquals(before, repository.fingerprint());
  }

  @Test
  void activeRoutesMapsEachColumn() throws Exception {
    db.route("/hello/{name}", "get,post", "script", "hello-script", null);
    db.setPathParams(1, "name, other");

    List<Route> routes = new RouteRepository(db).activeRoutes();

    assertEquals(1, routes.size());
    Route route = routes.get(0);
    assertEquals("/hello/{name}", route.getPath());
    assertEquals(List.of("GET", "POST"), route.getMethods());
    assertEquals(List.of("name", "other"), route.getPathParams());
    assertEquals("httpHandler", route.getHandlerMethod(), "a script route defaults its method");
  }

  @Test
  void activeRoutesQueryFiltersAndOrdersInSql() throws Exception {
    db.route("/one", "GET", "script", "s", "f");

    new RouteRepository(db).activeRoutes();

    String select = db.sql.stream().filter(s -> s.contains("sort_order")).findFirst().orElseThrow();
    assertTrue(select.contains("active"), select);
    assertTrue(select.contains("order by"), select);
  }

  @Test
  void isAvailableIsFalseWithoutADatabase() {
    DbPropertiesLoader.close();

    assertFalse(new RouteRepository(null).isAvailable());
    assertTrue(new RouteRepository(db).isAvailable());
  }
}
