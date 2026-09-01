package com.example.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.mongo.MongoReflectionClient;
import com.example.mongo.MongoSessionManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Covers handleCollections, handleFind and handleDisconnect using a mocked MongoReflectionClient.
 * handleConnect is not covered here: it calls the real MongoExtLibManager singleton, which
 * downloads the driver jar over the network — untestable without network access or a much larger
 * refactor to make the loader injectable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MongoHandlerTest {

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private StringWriter responseBody;
  private final MongoHandler handler = MongoHandler.getInstance();

  @BeforeEach
  void setUp() throws IOException {
    responseBody = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
  }

  @Test
  void handleCollections_sessionExpired_returns401() throws Exception {
    when(request.getReader())
        .thenReturn(reader("{\"sessionId\":\"does-not-exist\",\"database\":\"db\"}"));

    handler.handleCollections(request, response);

    verify(response).setStatus(401);
  }

  @Test
  void handleCollections_returns200WithCollectionNames() throws Exception {
    Object client = new Object();
    Object db = new Object();
    MongoReflectionClient reflectionClient = mock(MongoReflectionClient.class);
    when(reflectionClient.getDatabase(client, "mydb")).thenReturn(db);
    when(reflectionClient.listCollectionNames(db)).thenReturn(List.of("users", "orders"));
    String sessionId = MongoSessionManager.getInstance().createSession(client, reflectionClient);

    when(request.getReader())
        .thenReturn(reader("{\"sessionId\":\"" + sessionId + "\",\"database\":\"mydb\"}"));

    handler.handleCollections(request, response);

    verify(response).setStatus(200);
    String body = responseBody.toString();
    assertTrue(body.contains("users"));
    assertTrue(body.contains("orders"));
  }

  @Test
  void handleFind_sessionExpired_returns401() throws Exception {
    when(request.getReader())
        .thenReturn(
            reader("{\"sessionId\":\"does-not-exist\",\"database\":\"db\",\"collection\":\"c\"}"));

    handler.handleFind(request, response);

    verify(response).setStatus(401);
  }

  @Test
  void handleFind_returns200WithParsedDocuments() throws Exception {
    Object client = new Object();
    Object db = new Object();
    Object collection = new Object();
    MongoReflectionClient reflectionClient = mock(MongoReflectionClient.class);
    when(reflectionClient.getDatabase(client, "mydb")).thenReturn(db);
    when(reflectionClient.getCollection(db, "users")).thenReturn(collection);
    when(reflectionClient.find(collection, "", 100))
        .thenReturn(List.of("{\"_id\": 1, \"name\": \"alice\"}"));
    String sessionId = MongoSessionManager.getInstance().createSession(client, reflectionClient);

    when(request.getReader())
        .thenReturn(
            reader(
                "{\"sessionId\":\""
                    + sessionId
                    + "\",\"database\":\"mydb\",\"collection\":\"users\",\"filter\":\"\"}"));

    handler.handleFind(request, response);

    verify(response).setStatus(200);
    String body = responseBody.toString();
    assertTrue(body.contains("alice"));
  }

  @Test
  void handleDisconnect_sessionExpired_returns401() throws Exception {
    when(request.getReader()).thenReturn(reader("{\"sessionId\":\"does-not-exist\"}"));

    handler.handleDisconnect(request, response);

    verify(response).setStatus(401);
  }

  @Test
  void handleDisconnect_removesSession() throws Exception {
    Object client = new Object();
    MongoReflectionClient reflectionClient = mock(MongoReflectionClient.class);
    String sessionId = MongoSessionManager.getInstance().createSession(client, reflectionClient);
    when(request.getReader()).thenReturn(reader("{\"sessionId\":\"" + sessionId + "\"}"));

    handler.handleDisconnect(request, response);

    verify(response).setStatus(200);
    assertNull(MongoSessionManager.getInstance().getSession(sessionId));
  }

  private BufferedReader reader(String body) {
    return new BufferedReader(new StringReader(body));
  }
}
