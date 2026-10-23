package com.example.route;

import com.example.config.Database;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;

/**
 * In-memory stand-in for the routes table. {@link #route} adds a row. The fake answers the
 * fingerprint query and the route select, counts the selects, and can be told to fail.
 */
class FakeRoutesDb extends Database {

  private static final String[] COLUMNS = {
    "id",
    "path",
    "methods",
    "type",
    "handler",
    "handler_method",
    "processor",
    "resource",
    "content_type",
    "description",
    "path_params",
    "sort_order",
    "is_default",
    "active"
  };

  private final List<Object[]> rows = new ArrayList<>();
  private long nextId = 1;

  /** Number of times the route rows were read. */
  int tableReads;

  /** When true every statement fails, as if the database were down. */
  boolean failing;

  /** Every statement sent, lowercased. */
  final List<String> sql = new ArrayList<>();

  FakeRoutesDb() {
    super(null);
  }

  /** Adds an active, non-default route and returns its id. */
  long route(String path, String methods, String type, String handler, String handlerMethod) {
    return add(path, methods, type, handler, handlerMethod, false);
  }

  /** Adds a default route and returns its id. */
  long defaultRoute(
      String path, String methods, String type, String handler, String handlerMethod) {
    return add(path, methods, type, handler, handlerMethod, true);
  }

  private long add(
      String path,
      String methods,
      String type,
      String handler,
      String handlerMethod,
      boolean isDefault) {
    long id = nextId++;
    rows.add(
        new Object[] {
          id,
          path,
          methods,
          type,
          handler,
          handlerMethod,
          null,
          null,
          null,
          null,
          null,
          100,
          isDefault,
          true
        });
    return id;
  }

  /** Changes the handler of a route, which changes the table fingerprint. */
  void setHandler(long id, String handler) {
    rows.stream().filter(r -> r[0].equals(id)).forEach(r -> r[4] = handler);
  }

  /** Sets the comma separated path parameter names of a route. */
  void setPathParams(long id, String pathParams) {
    rows.stream().filter(r -> r[0].equals(id)).forEach(r -> r[10] = pathParams);
  }

  /** Sets the content type of every route whose path starts with the prefix. */
  void setJsonContentTypeFor(String pathPrefix) {
    rows.stream()
        .filter(
            r -> ((String) r[1]).startsWith(pathPrefix) && !((String) r[1]).endsWith("/download"))
        .forEach(r -> r[8] = "application/json");
  }

  /** Sets the content type of a route. */
  void setContentType(long id, String contentType) {
    rows.stream().filter(r -> r[0].equals(id)).forEach(r -> r[8] = contentType);
  }

  /** Sets the processor of a route. */
  void setProcessor(long id, String processor) {
    rows.stream().filter(r -> r[0].equals(id)).forEach(r -> r[6] = processor);
  }

  /** Sets the classpath resource of a static route. */
  void setResource(long id, String resource) {
    rows.stream().filter(r -> r[0].equals(id)).forEach(r -> r[7] = resource);
  }

  void remove(long id) {
    rows.removeIf(r -> r[0].equals(id));
  }

  @Override
  public <T> T query(Work<T> fn) throws Exception {
    return fn.run(
        DSL.using(
            new MockConnection(
                ctx -> {
                  if (failing) {
                    throw new SQLException("database is down");
                  }
                  String s = ctx.sql().toLowerCase();
                  sql.add(s);
                  if (s.contains("md5(")) {
                    return result(
                        List.<Object[]>of(new Object[] {Map.of("rows", rowsAsText()).toString()}),
                        "md5");
                  }
                  tableReads++;
                  return result(rows, COLUMNS);
                }),
            SQLDialect.POSTGRES));
  }

  private String rowsAsText() {
    StringBuilder text = new StringBuilder();
    rows.forEach(r -> text.append(java.util.Arrays.toString(r)));
    return text.toString();
  }

  @SuppressWarnings("unchecked")
  private static MockResult[] result(List<Object[]> data, String... columns) {
    var create = DSL.using(SQLDialect.POSTGRES);
    Field<?>[] fields = new Field<?>[columns.length];
    for (int i = 0; i < columns.length; i++) {
      fields[i] = DSL.field(DSL.name(columns[i]), Object.class);
    }
    Result<Record> result = create.newResult(fields);
    for (Object[] row : data) {
      Record record = create.newRecord(fields);
      for (int i = 0; i < fields.length; i++) {
        record.set((Field<Object>) fields[i], row[i]);
      }
      result.add(record);
    }
    return new MockResult[] {new MockResult(result.size(), result)};
  }
}
