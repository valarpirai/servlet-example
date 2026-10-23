package com.example.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
}
