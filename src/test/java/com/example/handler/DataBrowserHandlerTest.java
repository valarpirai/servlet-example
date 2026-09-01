package com.example.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.extlib.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Covers only the endpoints added for the db-type and post-connect database dropdowns
 * (handleDbTypes, handleDatabases, handleSelectDatabase). The PostgreSQL reconnect branch of
 * handleSelectDatabase is not covered here: it calls the real ExtLibManager singleton, which would
 * attempt a genuine JDBC connection — untestable without a live database or a much larger refactor
 * to make ExtLibManager injectable. The remaining handlers (connect, tables, query, disconnect,
 * driver-status, download) are pre-existing and still untested, a known gap.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataBrowserHandlerTest {

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private StringWriter responseBody;
  private final DataBrowserHandler handler = DataBrowserHandler.getInstance();

  @BeforeEach
  void setUp() throws IOException {
    responseBody = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
  }

  @Test
  void handleDbTypes_returns200WithAllKnownStrategies() throws Exception {
    handler.handleDbTypes(request, response);

    verify(response).setStatus(200);
    String body = responseBody.toString();
    assertTrue(body.contains("PostgreSQL"));
    assertTrue(body.contains("MySQL"));
    assertTrue(body.contains("Snowflake"));
  }

  @Test
  void handleDatabases_returns200WithCatalogsAndCurrent() throws Exception {
    Connection conn = mock(Connection.class);
    DatabaseMetaData meta = mock(DatabaseMetaData.class);
    ResultSet catalogs = mock(ResultSet.class);
    when(conn.getMetaData()).thenReturn(meta);
    when(meta.getCatalogs()).thenReturn(catalogs);
    when(catalogs.next()).thenReturn(true, true, false);
    when(catalogs.getString("TABLE_CAT")).thenReturn("db_one", "db_two");
    when(conn.getCatalog()).thenReturn("db_one");

    String sessionId =
        SessionManager.getInstance().createSession("mysql", "u", "p", Map.of(), conn);
    when(request.getReader()).thenReturn(reader("{\"sessionId\":\"" + sessionId + "\"}"));

    handler.handleDatabases(request, response);

    verify(response).setStatus(200);
    String body = responseBody.toString();
    assertTrue(body.contains("db_one"));
    assertTrue(body.contains("db_two"));
    assertTrue(body.contains("\"current\""));
  }

  @Test
  void handleDatabases_sessionExpired_returns401() throws Exception {
    when(request.getReader()).thenReturn(reader("{\"sessionId\":\"does-not-exist\"}"));

    handler.handleDatabases(request, response);

    verify(response).setStatus(401);
  }

  @Test
  void handleSelectDatabase_catalogSwitchSupported_callsSetCatalog() throws Exception {
    Connection conn = mock(Connection.class);
    String sessionId =
        SessionManager.getInstance().createSession("mysql", "u", "p", Map.of(), conn);
    when(request.getReader())
        .thenReturn(reader("{\"sessionId\":\"" + sessionId + "\",\"database\":\"newdb\"}"));

    handler.handleSelectDatabase(request, response);

    verify(conn).setCatalog("newdb");
    verify(response).setStatus(200);
  }

  @Test
  void handleSelectDatabase_sessionExpired_returns401() throws Exception {
    when(request.getReader())
        .thenReturn(reader("{\"sessionId\":\"does-not-exist\",\"database\":\"newdb\"}"));

    handler.handleSelectDatabase(request, response);

    verify(response).setStatus(401);
  }

  @Test
  void handleSelectDatabase_unknownDbTypeForSession_returns400() throws Exception {
    Connection conn = mock(Connection.class);
    String sessionId =
        SessionManager.getInstance().createSession("no-such-db", "u", "p", Map.of(), conn);
    when(request.getReader())
        .thenReturn(reader("{\"sessionId\":\"" + sessionId + "\",\"database\":\"newdb\"}"));

    handler.handleSelectDatabase(request, response);

    verify(response).setStatus(400);
  }

  private BufferedReader reader(String body) {
    return new BufferedReader(new StringReader(body));
  }
}
