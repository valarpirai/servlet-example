package com.example.servlet.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.config.Database;
import com.example.servlet.model.Attachment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatabaseAttachmentStorageTest {

  private static final int CHUNK_SIZE = 4;

  @Mock Database database;

  // Deep stubs let jOOQ fluent chains (insertInto().columns().values().execute()) return defaults
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  DSLContext dsl;

  private DatabaseAttachmentStorage storage;

  @BeforeEach
  void setUp() {
    storage = new DatabaseAttachmentStorage(database, CHUNK_SIZE);
  }

  // ---- store ----

  @Test
  void store_assignsId_andReturnsAttachment() throws Exception {
    // Execute the transact lambda with the deep-stubbed DSLContext
    doAnswer(
            inv -> {
              Database.Work<?> work = inv.getArgument(0);
              return work.run(dsl);
            })
        .when(database)
        .transact(any());

    Attachment a = newAttachment("test.txt", "text/plain");
    Attachment stored = storage.store(a, bytes("Hello"));

    assertNotNull(stored.getId());
    assertEquals("database", stored.getStorageType());
    assertTrue(stored.getStoragePath().startsWith("database:"));
    assertEquals(5, stored.getSizeBytes());
    assertNotNull(stored.getHash());
    assertTrue(stored.getHash().matches("[0-9a-f]{64}"));
  }

  @Test
  void store_throwsIOException_whenTransactFails() throws Exception {
    doThrow(new RuntimeException("DB error")).when(database).transact(any());

    assertThrows(
        IOException.class,
        () -> storage.store(newAttachment("fail.txt", "text/plain"), bytes("data")));
  }

  // ---- retrieve ----

  @Test
  void retrieve_throwsIOException_whenNotFound() throws Exception {
    doReturn(false).when(database).query(any());

    assertThrows(IOException.class, () -> storage.retrieve("unknown-id"));
  }

  // ---- delete ----

  @Test
  void delete_delegatesToDatabase() throws Exception {
    doReturn(1).when(database).query(any());

    storage.delete("some-id");

    verify(database).query(any());
  }

  @Test
  void delete_throwsIOException_onFailure() throws Exception {
    doThrow(new RuntimeException("disk error")).when(database).query(any());

    assertThrows(IOException.class, () -> storage.delete("some-id"));
  }

  // ---- exists ----

  @Test
  void exists_returnsTrue_whenFound() throws Exception {
    doReturn(true).when(database).query(any());

    assertTrue(storage.exists("abc-123"));
  }

  @Test
  void exists_returnsFalse_whenNotFound() throws Exception {
    doReturn(false).when(database).query(any());

    assertFalse(storage.exists("unknown"));
  }

  @Test
  void exists_returnsFalse_onException() throws Exception {
    doThrow(new RuntimeException("unreachable")).when(database).query(any());

    assertFalse(storage.exists("any-id"));
  }

  // ---- listAll ----

  @Test
  void listAll_returnsMappedAttachments() throws Exception {
    List<Attachment> attachments = List.of(attachment("attach-1"), attachment("attach-2"));
    doReturn(attachments).when(database).query(any());

    List<Attachment> list = storage.listAll();

    assertEquals(2, list.size());
    assertEquals("attach-1", list.get(0).getId());
  }

  @Test
  void listAll_throwsIOException_onFailure() throws Exception {
    doThrow(new RuntimeException("DB down")).when(database).query(any());

    assertThrows(IOException.class, () -> storage.listAll());
  }

  // ---- loadMetadata ----

  @Test
  void loadMetadata_returnsAttachment_whenFound() throws Exception {
    Attachment a = attachment("attach-1");
    doReturn(a).when(database).query(any());

    Attachment result = storage.loadMetadata("attach-1");

    assertNotNull(result);
    assertEquals("attach-1", result.getId());
  }

  @Test
  void loadMetadata_returnsNull_whenNotFound() throws Exception {
    doReturn(null).when(database).query(any());

    assertNull(storage.loadMetadata("missing"));
  }

  @Test
  void loadMetadata_throwsIOException_onFailure() throws Exception {
    doThrow(new RuntimeException("error")).when(database).query(any());

    assertThrows(IOException.class, () -> storage.loadMetadata("x"));
  }

  // ---- getStorageType ----

  @Test
  void getStorageType_returnsDatabase() {
    assertEquals("database", storage.getStorageType());
  }

  // ---- helpers ----

  private static ByteArrayInputStream bytes(String s) {
    return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
  }

  private static Attachment newAttachment(String fileName, String contentType) {
    Attachment a = new Attachment();
    a.setFileName(fileName);
    a.setContentType(contentType);
    return a;
  }

  private static Attachment attachment(String id) {
    Attachment a = new Attachment();
    a.setId(id);
    a.setFileName("file.txt");
    a.setContentType("text/plain");
    a.setSizeBytes(100L);
    a.setHash("abc123");
    a.setStorageType("database");
    return a;
  }
}
