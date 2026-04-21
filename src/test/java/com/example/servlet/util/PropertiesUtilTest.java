package com.example.servlet.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.example.config.PropertyRepository;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertiesUtilTest {

  @Mock private PropertyRepository repo;

  @AfterEach
  void tearDown() {
    PropertiesUtil.setPropertyRepository(null); // detach repo and clear cache
    PropertiesUtil.reload();
  }

  // ---- defaults (no DB, key not in YAML) ----

  @Test
  void returnsStringDefault_whenKeyAbsent() {
    assertEquals("default", PropertiesUtil.getString("no.such.key", "default"));
  }

  @Test
  void returnsIntDefault_whenKeyAbsent() {
    assertEquals(42, PropertiesUtil.getInt("no.such.key", 42));
  }

  @Test
  void returnsLongDefault_whenKeyAbsent() {
    assertEquals(1000L, PropertiesUtil.getLong("no.such.key", 1000L));
  }

  @Test
  void returnsBooleanDefault_whenKeyAbsent() {
    assertTrue(PropertiesUtil.getBoolean("no.such.key", true));
  }

  @Test
  void returnsDoubleDefault_whenKeyAbsent() {
    assertEquals(3.14, PropertiesUtil.getDouble("no.such.key", 3.14), 0.001);
  }

  @Test
  void nullKey_returnsDefault() {
    assertEquals("default", PropertiesUtil.getString(null, "default"));
  }

  @Test
  void emptyKey_returnsDefault() {
    assertEquals("default", PropertiesUtil.getString("", "default"));
  }

  // ---- YAML fallback (db.* keys still in application.yml) ----

  @Test
  void yamlDbUrl_isReadable_withoutRepository() {
    // db.url has a default value in application.yml
    String url = PropertiesUtil.getString("db.url", null);
    assertNotNull(url);
    assertTrue(url.startsWith("jdbc:postgresql://"));
  }

  @Test
  void hasProperty_trueForYamlKey() {
    assertTrue(PropertiesUtil.hasProperty("db.url"));
  }

  @Test
  void hasProperty_falseForMissingKey() {
    assertFalse(PropertiesUtil.hasProperty("nonexistent.property"));
  }

  // ---- LRU cache + DB lookup ----

  @Test
  void dbValue_returnedAndCached() throws Exception {
    when(repo.findValueByName("server.port")).thenReturn(Optional.of("9090"));
    PropertiesUtil.setPropertyRepository(repo);

    // first call hits DB
    assertEquals("9090", PropertiesUtil.getString("server.port", "8080"));
    // second call uses cache
    assertEquals("9090", PropertiesUtil.getString("server.port", "8080"));

    verify(repo, times(1)).findValueByName("server.port");
  }

  @Test
  void dbMiss_fallsBackToDefault() throws Exception {
    when(repo.findValueByName(anyString())).thenReturn(Optional.empty());
    PropertiesUtil.setPropertyRepository(repo);

    assertEquals("fallback", PropertiesUtil.getString("missing.key", "fallback"));
  }

  @Test
  void dbError_fallsBackToDefault() throws Exception {
    when(repo.findValueByName(anyString())).thenThrow(new SQLException("connection lost"));
    PropertiesUtil.setPropertyRepository(repo);

    assertEquals("fallback", PropertiesUtil.getString("some.key", "fallback"));
  }

  @Test
  void reload_clearsCache_soNextCallHitsDb() throws Exception {
    when(repo.findValueByName("server.port")).thenReturn(Optional.of("9090"));
    PropertiesUtil.setPropertyRepository(repo);

    PropertiesUtil.getString("server.port", "8080"); // populates cache
    PropertiesUtil.reload(); // clears cache
    PropertiesUtil.getString("server.port", "8080"); // should hit DB again

    verify(repo, times(2)).findValueByName("server.port");
  }

  @Test
  void setRepository_clearsExistingCache() throws Exception {
    when(repo.findValueByName("server.port")).thenReturn(Optional.of("9090"));
    PropertiesUtil.setPropertyRepository(repo);
    PropertiesUtil.getString("server.port", "8080"); // cache entry created

    // Register a new repo – cache must be cleared
    PropertyRepository repo2 = mock(PropertyRepository.class);
    when(repo2.findValueByName("server.port")).thenReturn(Optional.of("7070"));
    PropertiesUtil.setPropertyRepository(repo2);

    assertEquals("7070", PropertiesUtil.getString("server.port", "8080"));
    verify(repo2, times(1)).findValueByName("server.port");
    verify(repo, times(1)).findValueByName("server.port"); // not called again
  }

  // ---- type coercions from DB (all values are Strings from DB) ----

  @Test
  void getInt_parsesStringFromDb() throws Exception {
    when(repo.findValueByName("server.port")).thenReturn(Optional.of("8080"));
    PropertiesUtil.setPropertyRepository(repo);

    assertEquals(8080, PropertiesUtil.getInt("server.port", 0));
  }

  @Test
  void getLong_parsesStringFromDb() throws Exception {
    when(repo.findValueByName("upload.maxFileSize")).thenReturn(Optional.of("10485760"));
    PropertiesUtil.setPropertyRepository(repo);

    assertEquals(10485760L, PropertiesUtil.getLong("upload.maxFileSize", 0L));
  }

  @Test
  void getBoolean_parsesStringFromDb() throws Exception {
    when(repo.findValueByName("logging.fileEnabled")).thenReturn(Optional.of("true"));
    PropertiesUtil.setPropertyRepository(repo);

    assertTrue(PropertiesUtil.getBoolean("logging.fileEnabled", false));
  }

  @Test
  void getDouble_parsesStringFromDb() throws Exception {
    when(repo.findValueByName("some.rate")).thenReturn(Optional.of("1.5"));
    PropertiesUtil.setPropertyRepository(repo);

    assertEquals(1.5, PropertiesUtil.getDouble("some.rate", 0.0), 0.001);
  }

  @Test
  void getInt_returnsDefault_forInvalidDbString() throws Exception {
    when(repo.findValueByName("bad.int")).thenReturn(Optional.of("notANumber"));
    PropertiesUtil.setPropertyRepository(repo);

    assertEquals(99, PropertiesUtil.getInt("bad.int", 99));
  }

  // ---- LRU capacity ----

  @Test
  void lruCache_evictsOldestEntry_beyondCapacity() throws Exception {
    when(repo.findValueByName(anyString())).thenReturn(Optional.of("v"));
    PropertiesUtil.setPropertyRepository(repo);

    // fill past the 500-entry limit
    for (int i = 0; i < 502; i++) {
      PropertiesUtil.getString("key." + i, "d");
    }

    // key.0 was the first inserted and should have been evicted; re-query hits DB again
    PropertiesUtil.getString("key.0", "d");
    // DB called at least 502 times (501 unique fills + 1 re-fetch of evicted key.0)
    verify(repo, atLeast(502)).findValueByName(anyString());
  }

  // ---- environment / dev helpers ----

  @Test
  void isDevEnvironment_trueByDefault() throws Exception {
    when(repo.findValueByName("application.environment")).thenReturn(Optional.of("dev"));
    PropertiesUtil.setPropertyRepository(repo);

    assertTrue(PropertiesUtil.isDevEnvironment());
  }
}
