package com.example.mongo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class MongoSessionManagerTest {

  private final MongoSessionManager manager = MongoSessionManager.getInstance();

  @Test
  void createSession_returnsUniqueIds() {
    String id1 = manager.createSession(new Object(), mock(MongoReflectionClient.class));
    String id2 = manager.createSession(new Object(), mock(MongoReflectionClient.class));
    assertNotEquals(id1, id2);
  }

  @Test
  void getSession_returnsStoredClientAndReflectionClient() {
    Object client = new Object();
    MongoReflectionClient reflectionClient = mock(MongoReflectionClient.class);
    String id = manager.createSession(client, reflectionClient);

    MongoSessionManager.Session session = manager.getSession(id);

    assertNotNull(session);
    assertSame(client, session.client());
    assertSame(reflectionClient, session.reflectionClient());
  }

  @Test
  void getSession_unknownSession_returnsNull() {
    assertNull(manager.getSession("does-not-exist"));
  }

  @Test
  void removeSession_closesClientAndForgetsSession() throws Exception {
    Object client = new Object();
    MongoReflectionClient reflectionClient = mock(MongoReflectionClient.class);
    String id = manager.createSession(client, reflectionClient);

    manager.removeSession(id);

    assertNull(manager.getSession(id));
    verify(reflectionClient).close(client);
  }

  @Test
  void removeSession_unknownSession_isNoOp() {
    assertDoesNotThrow(() -> manager.removeSession("does-not-exist"));
  }
}
