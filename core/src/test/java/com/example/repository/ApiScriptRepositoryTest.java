package com.example.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.config.DbPropertiesLoader;
import com.example.processor.FakeScriptDb;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiScriptRepositoryTest {

  @Test
  void findsContentByName() throws Exception {
    FakeScriptDb db = new FakeScriptDb();
    db.endpoint("hello", "function httpHandler() {}");

    Optional<String> content = new ApiScriptRepository(db).findContent("hello");

    assertEquals(Optional.of("function httpHandler() {}"), content);
  }

  @Test
  void missingNameIsEmpty() throws Exception {
    Optional<String> content = new ApiScriptRepository(new FakeScriptDb()).findContent("nope");

    assertTrue(content.isEmpty());
  }

  @Test
  void noDatabaseThrowsNotReady() {
    DbPropertiesLoader.close();

    IOException e =
        assertThrows(IOException.class, () -> new ApiScriptRepository(null).findContent("hello"));

    assertEquals("API scripts are stored in the database, which is not ready", e.getMessage());
  }
}
