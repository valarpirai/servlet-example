package com.example.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.extlib.SessionManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataBrowserServiceTest {

  private final DataBrowserService service = new DataBrowserService();

  /** A connection whose one query returns column {@code a} with the given values, one per row. */
  private Connection connectionReturning(Object... values) throws Exception {
    Connection conn = mock(Connection.class);
    Statement stmt = mock(Statement.class);
    ResultSet rs = mock(ResultSet.class);
    ResultSetMetaData meta = mock(ResultSetMetaData.class);
    when(conn.createStatement()).thenReturn(stmt);
    when(stmt.executeQuery(anyString())).thenReturn(rs);
    when(rs.getMetaData()).thenReturn(meta);
    when(meta.getColumnCount()).thenReturn(1);
    when(meta.getColumnName(1)).thenReturn("a");
    Boolean[] more = new Boolean[values.length + 1];
    java.util.Arrays.fill(more, true);
    more[values.length] = false;
    when(rs.next())
        .thenReturn(values.length > 0, java.util.Arrays.copyOfRange(more, 1, more.length));
    if (values.length > 0) {
      Object first = values[0];
      Object[] rest = java.util.Arrays.copyOfRange(values, 1, values.length);
      when(rs.getObject(1)).thenReturn(first, rest);
    }
    return conn;
  }

  private String sessionOn(Connection conn) {
    return SessionManager.getInstance().createSession("pg", "u", "p", Map.of(), conn);
  }

  @Test
  void queryStripsTheSemicolonAndPagesTheSql() throws Exception {
    Connection conn = connectionReturning(5);
    String id = sessionOn(conn);

    Map<String, Object> out = service.query(id, "select 1 ;", 1);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(conn.createStatement()).executeQuery(sql.capture());
    assertEquals("SELECT * FROM (select 1) __q LIMIT 100 OFFSET 0", sql.getValue());
    assertEquals(List.of("a"), out.get("columns"));
    assertEquals(List.of(List.of(5)), out.get("rows"));
    assertEquals(1, out.get("page"));
    assertEquals(100, out.get("pageSize"));
    assertEquals(false, out.get("hasMore"));
  }

  @Test
  void queryOffsetsLaterPagesAndClampsPageBelowOne() throws Exception {
    Connection conn = connectionReturning();
    String id = sessionOn(conn);

    service.query(id, "select 1", 3);
    Map<String, Object> clamped = service.query(id, "select 1", 0);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(conn.createStatement(), org.mockito.Mockito.times(2)).executeQuery(sql.capture());
    assertTrue(sql.getAllValues().get(0).endsWith("LIMIT 100 OFFSET 200"));
    assertTrue(sql.getAllValues().get(1).endsWith("LIMIT 100 OFFSET 0"));
    assertEquals(1, clamped.get("page"));
  }

  @Test
  void queryTurnsBytesIntoBase64AndOtherValuesIntoText() throws Exception {
    Connection conn = connectionReturning(new byte[] {1, 2, 3}, true, "x");
    String id = sessionOn(conn);

    Map<String, Object> out = service.query(id, "select 1", 1);

    assertEquals(List.of(List.of("AQID"), List.of(true), List.of("x")), out.get("rows"));
  }

  @Test
  void aUnknownSessionIsExpiredForEveryOperation() {
    String gone = "no-such-session";

    assertThrows(DataBrowserService.SessionExpiredException.class, () -> service.databases(gone));
    assertThrows(
        DataBrowserService.SessionExpiredException.class,
        () -> service.tables(gone, Set.of("information_schema")));
    assertThrows(
        DataBrowserService.SessionExpiredException.class, () -> service.query(gone, "select 1", 1));
    assertThrows(DataBrowserService.SessionExpiredException.class, () -> service.disconnect(gone));
    assertThrows(
        DataBrowserService.SessionExpiredException.class,
        () -> service.selectDatabase(gone, "other"));
  }

  @Test
  void disconnectEndsTheSession() throws Exception {
    String id = sessionOn(mock(Connection.class));

    service.disconnect(id);

    assertThrows(DataBrowserService.SessionExpiredException.class, () -> service.disconnect(id));
  }
}
