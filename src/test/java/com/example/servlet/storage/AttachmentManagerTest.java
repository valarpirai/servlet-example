package com.example.servlet.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.example.servlet.model.Attachment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttachmentManagerTest {

  @TempDir Path tempDir;

  private AttachmentManager manager;

  @BeforeEach
  void setUp() throws Exception {
    System.setProperty("storage.filesystem.path", tempDir.toString());
    System.setProperty("storage.type", "filesystem");

    // Clean up and reset using helper
    StorageTestHelper.cleanupAttachmentsDirectory();
    StorageTestHelper.resetSingleton(AttachmentManager.class);

    manager = AttachmentManager.getInstance();
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("storage.filesystem.path");
    System.clearProperty("storage.type");
  }

  @Test
  void testGetInstance() {
    AttachmentManager instance1 = AttachmentManager.getInstance();
    AttachmentManager instance2 = AttachmentManager.getInstance();
    assertSame(instance1, instance2);
  }

  @Test
  void testStoreAndRetrieve() throws IOException {
    Attachment attachment = new Attachment();
    attachment.setFileName("test.txt");
    attachment.setContentType("text/plain");

    byte[] content = "Test content".getBytes();
    Attachment stored = manager.store(attachment, new ByteArrayInputStream(content));

    assertNotNull(stored.getId());
    assertEquals("test.txt", stored.getFileName());

    try (InputStream retrieved = manager.retrieve(stored.getId())) {
      byte[] retrievedContent = retrieved.readAllBytes();
      assertArrayEquals(content, retrievedContent);
    }
  }

  @Test
  void testGetMetadata() throws IOException {
    Attachment attachment = new Attachment();
    attachment.setFileName("metadata.txt");
    attachment.setContentType("text/plain");

    Attachment stored = manager.store(attachment, new ByteArrayInputStream("test".getBytes()));

    Attachment metadata = manager.getMetadata(stored.getId());
    assertNotNull(metadata);
    assertEquals(stored.getId(), metadata.getId());
    assertEquals("metadata.txt", metadata.getFileName());
  }

  @Test
  void testGetMetadataFromCache() throws IOException {
    Attachment attachment = new Attachment();
    attachment.setFileName("cached.txt");
    attachment.setContentType("text/plain");

    Attachment stored = manager.store(attachment, new ByteArrayInputStream("test".getBytes()));

    // First call loads to cache
    Attachment metadata1 = manager.getMetadata(stored.getId());
    // Second call should hit cache
    Attachment metadata2 = manager.getMetadata(stored.getId());

    assertSame(metadata1, metadata2);
  }

  @Test
  void testGetMetadataReturnsNullForNonexistent() {
    Attachment metadata = manager.getMetadata("nonexistent-id");
    assertNull(metadata);
  }

  @Test
  void testListAll() throws IOException {
    Attachment att1 = new Attachment();
    att1.setFileName("file1.txt");
    att1.setContentType("text/plain");

    Attachment att2 = new Attachment();
    att2.setFileName("file2.txt");
    att2.setContentType("text/plain");

    manager.store(att1, new ByteArrayInputStream("content1".getBytes()));
    manager.store(att2, new ByteArrayInputStream("content2".getBytes()));

    List<Attachment> all = manager.listAll();
    assertEquals(2, all.size());
  }

  @Test
  void testDelete() throws IOException {
    Attachment attachment = new Attachment();
    attachment.setFileName("delete.txt");
    attachment.setContentType("text/plain");

    Attachment stored = manager.store(attachment, new ByteArrayInputStream("test".getBytes()));
    String id = stored.getId();

    assertTrue(manager.exists(id));
    assertNotNull(manager.getMetadata(id));

    manager.delete(id);

    assertFalse(manager.exists(id));
    assertNull(manager.getMetadata(id));
  }

  @Test
  void testExists() throws IOException {
    assertFalse(manager.exists("nonexistent-id"));

    Attachment attachment = new Attachment();
    attachment.setFileName("exists.txt");
    attachment.setContentType("text/plain");

    Attachment stored = manager.store(attachment, new ByteArrayInputStream("test".getBytes()));
    assertTrue(manager.exists(stored.getId()));
  }

  @Test
  void testConcurrentStore() throws IOException {
    Attachment att1 = new Attachment();
    att1.setFileName("concurrent1.txt");
    att1.setContentType("text/plain");

    Attachment att2 = new Attachment();
    att2.setFileName("concurrent2.txt");
    att2.setContentType("text/plain");

    // Store two attachments
    Attachment stored1 = manager.store(att1, new ByteArrayInputStream("content1".getBytes()));
    Attachment stored2 = manager.store(att2, new ByteArrayInputStream("content2".getBytes()));

    // Both should be accessible
    assertNotNull(manager.getMetadata(stored1.getId()));
    assertNotNull(manager.getMetadata(stored2.getId()));
    assertNotEquals(stored1.getId(), stored2.getId());
  }
}
