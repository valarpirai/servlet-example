package com.example.extlib;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

  private final SessionManager manager = SessionManager.getInstance();

  @Test
  void createSession_returnsUniqueIds() {
    String id1 = manager.createSession("postgresql", "u", "p", Map.of(), mock(Connection.class));
    String id2 = manager.createSession("postgresql", "u", "p", Map.of(), mock(Connection.class));
    assertNotEquals(id1, id2);
  }

  @Test
  void getConnection_returnsConnectionForKnownSession() {
    Connection conn = mock(Connection.class);
    String id = manager.createSession("mysql", "u", "p", Map.of(), conn);
    assertSame(conn, manager.getConnection(id));
  }

  @Test
  void getConnection_unknownSession_returnsNull() {
    assertNull(manager.getConnection("does-not-exist"));
  }

  @Test
  void getSession_returnsStoredMetadata() {
    Map<String, String> props = Map.of("url", "jdbc:postgresql://localhost:5432/mydb");
    Connection conn = mock(Connection.class);
    String id = manager.createSession("postgresql", "alice", "secret", props, conn);

    SessionManager.Session session = manager.getSession(id);

    assertNotNull(session);
    assertEquals("postgresql", session.dbType());
    assertEquals("alice", session.user());
    assertEquals("secret", session.password());
    assertEquals(props, session.props());
    assertSame(conn, session.connection());
  }

  @Test
  void getSession_unknownSession_returnsNull() {
    assertNull(manager.getSession("does-not-exist"));
  }

  @Test
  void replaceConnection_swapsConnectionAndClosesOld() throws Exception {
    Connection oldConn = mock(Connection.class);
    Connection newConn = mock(Connection.class);
    String id = manager.createSession("mysql", "u", "p", Map.of(), oldConn);

    manager.replaceConnection(id, newConn);

    assertSame(newConn, manager.getConnection(id));
    verify(oldConn).close();
    verify(newConn, never()).close();
  }

  @Test
  void replaceConnection_unknownSession_closesNewConnection() throws Exception {
    Connection newConn = mock(Connection.class);
    manager.replaceConnection("does-not-exist", newConn);
    verify(newConn).close();
  }

  @Test
  void removeSession_closesConnectionAndForgetsSession() throws Exception {
    Connection conn = mock(Connection.class);
    String id = manager.createSession("snowflake", "u", "p", Map.of(), conn);

    manager.removeSession(id);

    assertNull(manager.getConnection(id));
    verify(conn).close();
  }
}
