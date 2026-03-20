package com.example.servlet.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.example.servlet.model.Attachment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileSystemStorageTest {

  @TempDir Path tempDir;

  private LocalFileSystemStorage storage;

  @BeforeEach
  void setUp() {
    // Set system property to use temp directory
    System.setProperty("storage.filesystem.path", tempDir.toString());
    System.setProperty("storage.chunkSize", "10");
    storage = new LocalFileSystemStorage();
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("storage.filesystem.path");
    System.clearProperty("storage.chunkSize");
  }

  @Test
  void testStoreAndRetrieve() throws IOException {
    Attachment attachment = new Attachment();
    attachment.setFileName("test.txt");
    attachment.setContentType("text/plain");

    byte[] content = "Hello, World!".getBytes();
    InputStream inputStream = new ByteArrayInputStream(content);

    Attachment stored = storage.store(attachment, inputStream);
    assertNotNull(stored.getId());
    assertEquals("test.txt", stored.getFileName());
    assertEquals(content.length, stored.getSizeBytes());
    assertEquals("filesystem", stored.getStorageType());
    assertNotNull(stored.getHash());

    // Retrieve and verify
    try (InputStream retrieved = storage.retrieve(stored.getId())) {
      byte[] retrievedContent = retrieved.readAllBytes();
      assertArrayEquals(content, retrievedContent);
    }
  }

  @Test
  void testStoreCreatesChunks() throws IOException {
    Attachment attachment = new Attachment();
    attachment.setFileName("chunked.txt");
    attachment.setContentType("text/plain");

    // 25 bytes with 10-byte chunks = 3 chunks
    byte[] content = new byte[25];
    for (int i = 0; i < content.length; i++) {
      content[i] = (byte) i;
    }

    Attachment stored = storage.store(attachment, new ByteArrayInputStream(content));

    // Verify chunks exist
    Path attachmentDir = tempDir.resolve(stored.getId());
    assertTrue(Files.exists(attachmentDir.resolve("chunk_0")));
    assertTrue(Files.exists(attachmentDir.resolve("chunk_1")));
    assertTrue(Files.exists(attachmentDir.resolve("chunk_2")));
    assertFalse(Files.exists(attachmentDir.resolve("chunk_3")));
  }

  @Test
  void testStoreCreatesMetadata() throws IOException {
    Attachment attachment = new Attachment();
    attachment.setFileName("metadata.txt");
    attachment.setContentType("text/plain");

    Attachment stored = storage.store(attachment, new ByteArrayInputStream("test".getBytes()));

    Path metadataFile = tempDir.resolve(stored.getId()).resolve("metadata.json");
    assertTrue(Files.exists(metadataFile));

    // Verify metadata can be loaded
    Attachment loaded = storage.loadMetadata(stored.getId());
    assertNotNull(loaded);
    assertEquals(stored.getId(), loaded.getId());
    assertEquals("metadata.txt", loaded.getFileName());
  }

  @Test
  void testDelete() throws IOException {
    Attachment attachment = new Attachment();
    attachment.setFileName("delete.txt");
    attachment.setContentType("text/plain");

    Attachment stored = storage.store(attachment, new ByteArrayInputStream("test".getBytes()));
    String id = stored.getId();

    assertTrue(storage.exists(id));

    storage.delete(id);

    assertFalse(storage.exists(id));
    assertFalse(Files.exists(tempDir.resolve(id)));
  }

  @Test
  void testExists() throws IOException {
    Attachment attachment = new Attachment();
    attachment.setFileName("exists.txt");
    attachment.setContentType("text/plain");

    assertFalse(storage.exists("nonexistent-id"));

    Attachment stored = storage.store(attachment, new ByteArrayInputStream("test".getBytes()));
    assertTrue(storage.exists(stored.getId()));
  }

  @Test
  void testListAll() throws IOException {
    Attachment att1 = new Attachment();
    att1.setFileName("file1.txt");
    att1.setContentType("text/plain");

    Attachment att2 = new Attachment();
    att2.setFileName("file2.txt");
    att2.setContentType("text/plain");

    storage.store(att1, new ByteArrayInputStream("content1".getBytes()));
    storage.store(att2, new ByteArrayInputStream("content2".getBytes()));

    List<Attachment> all = storage.listAll();
    assertEquals(2, all.size());
  }

  @Test
  void testRetrieveNonexistent() {
    assertThrows(IOException.class, () -> storage.retrieve("nonexistent-id"));
  }

  @Test
  void testHashCalculation() throws IOException {
    Attachment attachment = new Attachment();
    attachment.setFileName("hash.txt");
    attachment.setContentType("text/plain");

    byte[] content = "test content".getBytes();
    Attachment stored = storage.store(attachment, new ByteArrayInputStream(content));

    assertNotNull(stored.getHash());
    assertFalse(stored.getHash().isEmpty());
    assertEquals(64, stored.getHash().length()); // SHA-256 hex = 64 chars
  }

  @Test
  void testLargeFileChunking() throws IOException {
    Attachment attachment = new Attachment();
    attachment.setFileName("large.bin");
    attachment.setContentType("application/octet-stream");

    // Create 100 bytes (10 chunks with 10-byte chunk size)
    byte[] content = new byte[100];
    for (int i = 0; i < content.length; i++) {
      content[i] = (byte) (i % 256);
    }

    Attachment stored = storage.store(attachment, new ByteArrayInputStream(content));
    assertEquals(100, stored.getSizeBytes());

    // Verify all content retrieved correctly
    try (InputStream retrieved = storage.retrieve(stored.getId())) {
      byte[] retrievedContent = retrieved.readAllBytes();
      assertArrayEquals(content, retrievedContent);
    }
  }

  @Test
  void testGetStorageType() {
    assertEquals("filesystem", storage.getStorageType());
  }

  @Test
  void testLoadMetadataReturnsNullForNonexistent() throws IOException {
    Attachment loaded = storage.loadMetadata("nonexistent-id");
    assertNull(loaded);
  }
}
