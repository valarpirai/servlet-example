package com.example.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyRepositoryTest {

  @Mock Database database;

  PropertyRepository repo;

  @BeforeEach
  void setUp() {
    repo = new PropertyRepository(database);
  }

  // ---- findValueByName ----

  @Test
  void findValueByName_returnsValue_whenRowExists() throws Exception {
    when(database.query(any())).thenReturn(Optional.of("8080"));

    Optional<String> result = repo.findValueByName("server.port");

    assertTrue(result.isPresent());
    assertEquals("8080", result.get());
    verify(database).query(any());
  }

  @Test
  void findValueByName_returnsEmpty_whenNoRow() throws Exception {
    when(database.query(any())).thenReturn(Optional.empty());

    assertFalse(repo.findValueByName("missing.key").isPresent());
  }

  @Test
  void findValueByName_returnsEmptyOptional_whenValueIsNull() throws Exception {
    when(database.query(any())).thenReturn(Optional.empty());

    assertFalse(repo.findValueByName("null.valued").isPresent());
  }

  @Test
  void findValueByName_propagatesException() throws Exception {
    doThrow(new RuntimeException("DB error")).when(database).query(any());

    assertThrows(RuntimeException.class, () -> repo.findValueByName("any.key"));
  }

  // ---- findAll ----

  @Test
  void findAll_returnsMappedRows() throws Exception {
    List<AppProperty> rows = List.of(prop(1L, "a.key", "v1"), prop(2L, "b.key", "v2"));
    when(database.query(any())).thenReturn(rows);

    List<AppProperty> list = repo.findAll();

    assertEquals(2, list.size());
  }

  @Test
  void findAll_returnsEmptyList_whenNoRows() throws Exception {
    when(database.query(any())).thenReturn(List.of());

    assertTrue(repo.findAll().isEmpty());
  }

  // ---- findById ----

  @Test
  void findById_returnsProperty_whenFound() throws Exception {
    AppProperty p = prop(5L, "my.prop", "hello");
    when(database.query(any())).thenReturn(Optional.of(p));

    Optional<AppProperty> result = repo.findById(5L);

    assertTrue(result.isPresent());
    assertEquals("my.prop", result.get().name());
    assertEquals("hello", result.get().value());
  }

  @Test
  void findById_returnsEmpty_whenNotFound() throws Exception {
    when(database.query(any())).thenReturn(Optional.empty());

    assertFalse(repo.findById(99L).isPresent());
  }

  // ---- create ----

  @Test
  void create_returnsCreatedProperty() throws Exception {
    AppProperty created = prop(10L, "new.prop", "newVal");
    when(database.query(any())).thenReturn(created);

    AppProperty result = repo.create("new.prop", "newVal", "STRING", "desc", "admin");

    assertEquals(10L, result.id());
    assertEquals("new.prop", result.name());
  }

  @Test
  void create_delegatesToDatabase() throws Exception {
    when(database.query(any())).thenReturn(prop(11L, "x", "y"));

    repo.create("x", "y", null, null, null);

    verify(database).query(any());
  }

  // ---- update ----

  @Test
  void update_returnsUpdatedRow() throws Exception {
    AppProperty updated = prop(3L, "some.key", "newValue");
    when(database.query(any())).thenReturn(Optional.of(updated));

    Optional<AppProperty> result = repo.update(3L, "newValue", "updated desc", "admin");

    assertTrue(result.isPresent());
    assertEquals("newValue", result.get().value());
  }

  @Test
  void update_returnsEmpty_whenIdNotFound() throws Exception {
    when(database.query(any())).thenReturn(Optional.empty());

    assertFalse(repo.update(999L, "v", "d", "admin").isPresent());
  }

  // ---- setActive ----

  @Test
  void setActive_returnsTrue_whenRowUpdated() throws Exception {
    when(database.query(any())).thenReturn(true);

    assertTrue(repo.setActive(1L, false, "admin"));
  }

  @Test
  void setActive_returnsFalse_whenRowNotFound() throws Exception {
    when(database.query(any())).thenReturn(false);

    assertFalse(repo.setActive(999L, true, "admin"));
  }

  // ---- delete ----

  @Test
  void delete_returnsTrue_whenRowDeleted() throws Exception {
    when(database.query(any())).thenReturn(true);

    assertTrue(repo.delete(1L));
  }

  @Test
  void delete_returnsFalse_whenRowNotFound() throws Exception {
    when(database.query(any())).thenReturn(false);

    assertFalse(repo.delete(999L));
  }

  // ---- helpers ----

  private AppProperty prop(long id, String name, String value) {
    Instant now = Instant.now();
    return new AppProperty(id, name, value, "STRING", "desc", true, now, now, "system", "system");
  }
}
