package com.example.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatabaseTest {

  @Mock private DbConfig config;
  @Mock private Connection connection;

  private Database database;

  @BeforeEach
  void setUp() throws SQLException {
    when(config.getConnection()).thenReturn(connection);
    database = new Database(config);
  }

  @Test
  void query_returnsLambdaResult() throws Exception {
    String result = database.query(dsl -> "hello");
    assertEquals("hello", result);
  }

  @Test
  void query_closesConnectionAfterSuccess() throws Exception {
    database.query(dsl -> null);
    verify(connection).close();
  }

  @Test
  void query_closesConnectionOnException() throws Exception {
    assertThrows(
        RuntimeException.class,
        () ->
            database.query(
                dsl -> {
                  throw new RuntimeException("boom");
                }));
    verify(connection).close();
  }

  @Test
  void transact_commitsOnSuccess() throws Exception {
    database.transact(dsl -> "ok");
    verify(connection).setAutoCommit(false);
    verify(connection).commit();
    verify(connection, never()).rollback();
    verify(connection).close();
  }

  @Test
  void transact_rollsBackAndRethrowsOnException() throws Exception {
    RuntimeException cause = new RuntimeException("fail");
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                database.transact(
                    dsl -> {
                      throw cause;
                    }));
    assertSame(cause, thrown);
    verify(connection).rollback();
    verify(connection, never()).commit();
    verify(connection).close();
  }

  @Test
  void openConnection_returnsRawConnection() throws SQLException {
    Connection c = database.openConnection();
    assertSame(connection, c);
  }

  @Test
  void query_propagatesCheckedException() {
    assertThrows(
        Exception.class,
        () ->
            database.query(
                dsl -> {
                  throw new java.io.IOException("io error");
                }));
  }
}
