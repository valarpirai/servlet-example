package com.example.servlet.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.config.AppProperty;
import com.example.config.DbPropertiesLoader;
import com.example.config.PropertyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PropertiesHandlerTest {

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private PropertyRepository repo;

  private MockedStatic<DbPropertiesLoader> loaderMock;
  private StringWriter responseBody;

  private final PropertiesHandler handler = PropertiesHandler.getInstance();

  @BeforeEach
  void setUp() throws IOException {
    loaderMock = mockStatic(DbPropertiesLoader.class);
    loaderMock.when(DbPropertiesLoader::getRepository).thenReturn(repo);

    responseBody = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
  }

  @AfterEach
  void tearDown() {
    loaderMock.close();
  }

  // ---- handleList ----

  @Test
  void handleList_returns200WithProperties() throws Exception {
    when(repo.findAll()).thenReturn(List.of(prop(1L, "server.port", "8080")));

    handler.handleList(request, response);

    verify(response).setStatus(200);
    assertTrue(responseBody.toString().contains("server.port"));
  }

  @Test
  void handleList_returns503_whenNoRepo() throws Exception {
    loaderMock.when(DbPropertiesLoader::getRepository).thenReturn(null);

    handler.handleList(request, response);

    verify(response).setStatus(503);
  }

  @Test
  void handleList_returns500_onRepoException() throws Exception {
    when(repo.findAll()).thenThrow(new RuntimeException("DB error"));

    handler.handleList(request, response);

    verify(response).setStatus(500);
  }

  // ---- handleGet ----

  @Test
  void handleGet_returns200_whenFound() throws Exception {
    when(repo.findById(5L)).thenReturn(Optional.of(prop(5L, "my.key", "val")));

    handler.handleGet(request, response, "5");

    verify(response).setStatus(200);
    assertTrue(responseBody.toString().contains("my.key"));
  }

  @Test
  void handleGet_returns404_whenNotFound() throws Exception {
    when(repo.findById(99L)).thenReturn(Optional.empty());

    handler.handleGet(request, response, "99");

    verify(response).setStatus(404);
  }

  @Test
  void handleGet_returns503_whenNoRepo() throws Exception {
    loaderMock.when(DbPropertiesLoader::getRepository).thenReturn(null);

    handler.handleGet(request, response, "1");

    verify(response).setStatus(503);
  }

  // ---- handleCreate ----

  @Test
  void handleCreate_returns201_withCreatedProperty() throws Exception {
    String body =
        "{\"name\":\"new.prop\",\"value\":\"42\",\"type\":\"INTEGER\",\"description\":\"d\"}";
    when(request.getReader()).thenReturn(reader(body));
    when(repo.create("new.prop", "42", "INTEGER", "d", "api"))
        .thenReturn(prop(10L, "new.prop", "42"));

    handler.handleCreate(request, response);

    verify(response).setStatus(201);
    assertTrue(responseBody.toString().contains("new.prop"));
  }

  @Test
  void handleCreate_returns400_whenNameMissing() throws Exception {
    when(request.getReader()).thenReturn(reader("{\"value\":\"x\"}"));

    handler.handleCreate(request, response);

    verify(response).setStatus(400);
  }

  // ---- handleUpdate ----

  @Test
  void handleUpdate_returns200_whenFound() throws Exception {
    String body = "{\"value\":\"9090\",\"description\":\"updated\"}";
    when(request.getReader()).thenReturn(reader(body));
    when(repo.update(3L, "9090", "updated", "api"))
        .thenReturn(Optional.of(prop(3L, "server.port", "9090")));

    handler.handleUpdate(request, response, "3");

    verify(response).setStatus(200);
  }

  @Test
  void handleUpdate_returns404_whenNotFound() throws Exception {
    when(request.getReader()).thenReturn(reader("{\"value\":\"x\"}"));
    when(repo.update(anyLong(), any(), any(), any())).thenReturn(Optional.empty());

    handler.handleUpdate(request, response, "99");

    verify(response).setStatus(404);
  }

  // ---- handleToggleActive ----

  @Test
  void handleToggleActive_returns200_whenUpdated() throws Exception {
    when(request.getReader()).thenReturn(reader("{\"active\":false}"));
    when(repo.setActive(2L, false, "api")).thenReturn(true);

    handler.handleToggleActive(request, response, "2");

    verify(response).setStatus(200);
    assertTrue(responseBody.toString().contains("\"active\""));
  }

  @Test
  void handleToggleActive_returns404_whenNotFound() throws Exception {
    when(request.getReader()).thenReturn(reader("{\"active\":true}"));
    when(repo.setActive(anyLong(), anyBoolean(), any())).thenReturn(false);

    handler.handleToggleActive(request, response, "99");

    verify(response).setStatus(404);
  }

  @Test
  void handleToggleActive_returns400_whenActiveMissing() throws Exception {
    when(request.getReader()).thenReturn(reader("{}"));

    handler.handleToggleActive(request, response, "1");

    verify(response).setStatus(400);
  }

  // ---- handleDelete ----

  @Test
  void handleDelete_returns200_whenDeleted() throws Exception {
    when(repo.delete(4L)).thenReturn(true);

    handler.handleDelete(request, response, "4");

    verify(response).setStatus(200);
    assertTrue(responseBody.toString().contains("\"deleted\""));
  }

  @Test
  void handleDelete_returns404_whenNotFound() throws Exception {
    when(repo.delete(anyLong())).thenReturn(false);

    handler.handleDelete(request, response, "99");

    verify(response).setStatus(404);
  }

  // ---- handleReload ----

  @Test
  void handleReload_returns200() throws Exception {
    handler.handleReload(request, response);

    verify(response).setStatus(200);
    assertTrue(responseBody.toString().contains("\"reloaded\""));
  }

  // ---- helpers ----

  private AppProperty prop(long id, String name, String value) {
    Instant now = Instant.now();
    return new AppProperty(id, name, value, "STRING", "desc", true, now, now, "system", "system");
  }

  private BufferedReader reader(String body) {
    return new BufferedReader(new StringReader(body));
  }
}
