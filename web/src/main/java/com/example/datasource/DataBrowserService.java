package com.example.datasource;

import com.example.extlib.ExtLibManager;
import com.example.extlib.SessionManager;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The JDBC work behind the data browser: connect to an external database, list its catalogs and
 * tables, and run a paged query. It returns plain maps and lists. The handler turns them into
 * responses.
 */
public class DataBrowserService {

  private static final int PAGE_SIZE = 100;

  /** The session id has no live connection. */
  public static class SessionExpiredException extends RuntimeException {
    public SessionExpiredException() {
      super("Session not found or expired");
    }
  }

  /** The session's database type has no registered strategy. */
  public static class UnknownDbTypeException extends RuntimeException {
    public UnknownDbTypeException() {
      super("Unknown dbType for session");
    }
  }

  /**
   * Opens a connection and a session for it.
   *
   * @param strategy the strategy that builds the JDBC URL
   * @param dbType the database type key
   * @param user the user name
   * @param password the password
   * @param props the connection properties
   * @return the new session id
   * @throws Exception when the connection fails
   */
  public String connect(
      DataSourceStrategy strategy,
      String dbType,
      String user,
      String password,
      Map<String, String> props)
      throws Exception {
    String url = strategy.buildUrl(props);
    Connection conn = ExtLibManager.getInstance().connect(dbType, url, user, password, props);
    return SessionManager.getInstance().createSession(dbType, user, password, props, conn);
  }

  /**
   * Lists the catalogs on the connected server.
   *
   * @param sessionId the session
   * @return a map with {@code databases} and {@code current}
   * @throws SessionExpiredException when the session is gone
   * @throws Exception when the metadata query fails
   */
  public Map<String, Object> databases(String sessionId) throws Exception {
    Connection conn = connection(sessionId);
    List<String> databases = new ArrayList<>();
    try (ResultSet rs = conn.getMetaData().getCatalogs()) {
      while (rs.next()) databases.add(rs.getString("TABLE_CAT"));
    }
    String current = conn.getCatalog();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("databases", databases);
    data.put("current", current == null ? "" : current);
    return data;
  }

  /**
   * Switches the session to another database. Catalog-switching databases change catalog. Others
   * reconnect with a new URL.
   *
   * @param sessionId the session
   * @param database the database to switch to
   * @throws SessionExpiredException when the session is gone
   * @throws UnknownDbTypeException when the session's database type has no strategy
   * @throws Exception when the switch fails
   */
  public void selectDatabase(String sessionId, String database) throws Exception {
    SessionManager.Session session = SessionManager.getInstance().getSession(sessionId);
    if (session == null) {
      throw new SessionExpiredException();
    }
    DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(session.dbType());
    if (strategy == null) {
      throw new UnknownDbTypeException();
    }
    if (strategy.supportsCatalogSwitch()) {
      session.connection().setCatalog(database);
    } else {
      String newUrl = strategy.buildUrlForDatabase(session.props(), database);
      Connection newConn =
          ExtLibManager.getInstance()
              .connect(
                  session.dbType(), newUrl, session.user(), session.password(), session.props());
      SessionManager.getInstance().replaceConnection(sessionId, newConn);
    }
  }

  /**
   * Lists the tables and views of the connected database, by schema. Schemas with no tables are
   * left out.
   *
   * @param sessionId the session
   * @param systemSchemas lower-case schema names to hide
   * @return one entry per schema, with keys {@code schema} and {@code tables}
   * @throws SessionExpiredException when the session is gone
   * @throws Exception when the metadata query fails
   */
  public List<Map<String, Object>> tables(String sessionId, Set<String> systemSchemas)
      throws Exception {
    Connection conn = connection(sessionId);
    DatabaseMetaData meta = conn.getMetaData();
    // Scope to the currently-connected database only
    String catalog = conn.getCatalog();
    Map<String, List<String>> schemaMap = new LinkedHashMap<>();

    // getSchemas(catalog, schemaPattern) scopes to the current catalog,
    // preventing MySQL from listing all databases as schemas
    try (ResultSet schemas = meta.getSchemas(catalog, null)) {
      while (schemas.next()) {
        String name = schemas.getString("TABLE_SCHEM");
        if (!systemSchemas.contains(name.toLowerCase())) {
          schemaMap.put(name, new ArrayList<>());
        }
      }
    }

    if (schemaMap.isEmpty()) schemaMap.put("default", new ArrayList<>());

    for (String schema : schemaMap.keySet()) {
      String schemaArg = "default".equals(schema) ? null : schema;
      try (ResultSet tables =
          meta.getTables(catalog, schemaArg, "%", new String[] {"TABLE", "VIEW"})) {
        while (tables.next()) {
          schemaMap.get(schema).add(tables.getString("TABLE_NAME"));
        }
      }
    }

    List<Map<String, Object>> result = new ArrayList<>();
    schemaMap.forEach(
        (schema, tables) -> {
          if (!tables.isEmpty()) {
            result.add(Map.of("schema", schema, "tables", tables));
          }
        });
    return result;
  }

  /**
   * Runs a query and returns one page of it.
   *
   * @param sessionId the session
   * @param sql the query; a trailing semicolon is dropped
   * @param page the 1-based page; anything below 1 means 1
   * @return a map with {@code columns}, {@code rows}, {@code page}, {@code pageSize} and {@code
   *     hasMore}
   * @throws SessionExpiredException when the session is gone
   * @throws Exception when the query fails
   */
  public Map<String, Object> query(String sessionId, String sql, int page) throws Exception {
    int pageNumber = Math.max(page, 1);
    int offset = (pageNumber - 1) * PAGE_SIZE;
    Connection conn = connection(sessionId);

    String text = sql.strip();
    if (text.endsWith(";")) text = text.substring(0, text.length() - 1).stripTrailing();
    String pagedSql = "SELECT * FROM (" + text + ") __q LIMIT " + PAGE_SIZE + " OFFSET " + offset;

    List<String> columns = new ArrayList<>();
    List<List<Object>> rows = new ArrayList<>();

    try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(pagedSql)) {

      ResultSetMetaData rsMeta = rs.getMetaData();
      int colCount = rsMeta.getColumnCount();
      for (int i = 1; i <= colCount; i++) columns.add(rsMeta.getColumnName(i));

      while (rs.next()) {
        List<Object> row = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) row.add(toSafeValue(rs.getObject(i)));
        rows.add(row);
      }
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("columns", columns);
    data.put("rows", rows);
    data.put("page", pageNumber);
    data.put("pageSize", PAGE_SIZE);
    data.put("hasMore", rows.size() == PAGE_SIZE);
    return data;
  }

  /**
   * Ends a session and closes its connection.
   *
   * @param sessionId the session
   * @throws SessionExpiredException when the session is gone
   */
  public void disconnect(String sessionId) {
    connection(sessionId);
    SessionManager.getInstance().removeSession(sessionId);
  }

  private Connection connection(String sessionId) {
    Connection conn = SessionManager.getInstance().getConnection(sessionId);
    if (conn == null) {
      throw new SessionExpiredException();
    }
    return conn;
  }

  private static Object toSafeValue(Object value) {
    if (value == null) return null;
    if (value instanceof Number || value instanceof Boolean) return value;
    if (value instanceof byte[]) return Base64.getEncoder().encodeToString((byte[]) value);
    return value.toString();
  }
}
