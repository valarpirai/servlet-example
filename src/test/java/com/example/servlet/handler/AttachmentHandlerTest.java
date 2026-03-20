package com.example.servlet.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.servlet.model.Attachment;
import com.example.servlet.storage.AttachmentManager;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AttachmentHandlerTest {

  @TempDir Path tempDir;

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private AttachmentHandler handler;
  private AttachmentManager attachmentManager;
  private StringWriter responseWriter;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    System.setProperty("storage.filesystem.path", tempDir.toString());
    System.setProperty("storage.type", "filesystem");

    // Clean up and reset using helper
    com.example.servlet.storage.StorageTestHelper.cleanupAttachmentsDirectory();
    com.example.servlet.storage.StorageTestHelper.resetSingleton(AttachmentManager.class);
    com.example.servlet.storage.StorageTestHelper.resetSingleton(AttachmentHandler.class);

    handler = AttachmentHandler.getInstance();
    attachmentManager = AttachmentManager.getInstance();

    responseWriter = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("storage.filesystem.path");
    System.clearProperty("storage.type");
  }

  @Test
  void testGetInstance() {
    AttachmentHandler instance1 = AttachmentHandler.getInstance();
    AttachmentHandler instance2 = AttachmentHandler.getInstance();
    assertSame(instance1, instance2);
  }

  @Test
  void testHandleDownloadSuccess() throws IOException {
    // Store a test attachment
    Attachment attachment = new Attachment();
    attachment.setFileName("test.txt");
    attachment.setContentType("text/plain");

    byte[] content = "Test content".getBytes();
    Attachment stored = attachmentManager.store(attachment, new ByteArrayInputStream(content));

    // Mock servlet output stream
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ServletOutputStream servletOutputStream =
        new ServletOutputStream() {
          @Override
          public void write(int b) throws IOException {
            outputStream.write(b);
          }

          @Override
          public boolean isReady() {
            return true;
          }

          @Override
          public void setWriteListener(jakarta.servlet.WriteListener writeListener) {}
        };

    when(response.getOutputStream()).thenReturn(servletOutputStream);

    handler.handleDownload(request, response, stored.getId());

    verify(response).setContentType("text/plain");
    verify(response).setHeader("Content-Disposition", "attachment; filename=\"test.txt\"");
    verify(response).setContentLengthLong(content.length);

    byte[] downloadedContent = outputStream.toByteArray();
    assertArrayEquals(content, downloadedContent);
  }

  @Test
  void testHandleDownloadNotFound() throws IOException {
    handler.handleDownload(request, response, "nonexistent-id");

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    verify(response).setContentType("application/json");

    String responseBody = responseWriter.toString();
    assertTrue(responseBody.contains("Attachment not found"));
  }

  @Test
  void testHandleMetadataSuccess() throws IOException {
    // Store a test attachment
    Attachment attachment = new Attachment();
    attachment.setFileName("metadata.txt");
    attachment.setContentType("text/plain");

    Attachment stored =
        attachmentManager.store(attachment, new ByteArrayInputStream("test".getBytes()));

    handler.handleMetadata(response, stored.getId());

    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setContentType("application/json");

    String responseBody = responseWriter.toString();
    assertTrue(responseBody.contains("metadata.txt"));
    assertTrue(responseBody.contains(stored.getId()));
  }

  @Test
  void testHandleMetadataNotFound() throws IOException {
    handler.handleMetadata(response, "nonexistent-id");

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    verify(response).setContentType("application/json");

    String responseBody = responseWriter.toString();
    assertTrue(responseBody.contains("Attachment not found"));
  }

  @Test
  void testHandleList() throws IOException {
    // Store two test attachments
    Attachment att1 = new Attachment();
    att1.setFileName("file1.txt");
    att1.setContentType("text/plain");

    Attachment att2 = new Attachment();
    att2.setFileName("file2.txt");
    att2.setContentType("text/plain");

    attachmentManager.store(att1, new ByteArrayInputStream("content1".getBytes()));
    attachmentManager.store(att2, new ByteArrayInputStream("content2".getBytes()));

    handler.handleList(response);

    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setContentType("application/json");

    String responseBody = responseWriter.toString();
    assertTrue(responseBody.contains("file1.txt"), "file1.txt not found in: " + responseBody);
    assertTrue(responseBody.contains("file2.txt"), "file2.txt not found in: " + responseBody);
    assertTrue(responseBody.contains("\"count\": 2"), "count: 2 not found in: " + responseBody);
  }

  @Test
  void testHandleDeleteSuccess() throws IOException {
    // Store a test attachment
    Attachment attachment = new Attachment();
    attachment.setFileName("delete.txt");
    attachment.setContentType("text/plain");

    Attachment stored =
        attachmentManager.store(attachment, new ByteArrayInputStream("test".getBytes()));
    String id = stored.getId();

    handler.handleDelete(response, id);

    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setContentType("application/json");

    String responseBody = responseWriter.toString();
    assertTrue(responseBody.contains("Attachment deleted"));
    assertTrue(responseBody.contains(id));

    // Verify attachment was actually deleted
    assertFalse(attachmentManager.exists(id));
  }

  @Test
  void testHandleDeleteNotFound() throws IOException {
    handler.handleDelete(response, "nonexistent-id");

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    verify(response).setContentType("application/json");

    String responseBody = responseWriter.toString();
    assertTrue(responseBody.contains("Attachment not found"));
  }

  @Test
  void testHandleDownloadWithDefaultContentType() throws IOException {
    // Store attachment with null content type
    Attachment attachment = new Attachment();
    attachment.setFileName("test.bin");
    attachment.setContentType(null);

    Attachment stored =
        attachmentManager.store(attachment, new ByteArrayInputStream("data".getBytes()));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ServletOutputStream servletOutputStream =
        new ServletOutputStream() {
          @Override
          public void write(int b) throws IOException {
            outputStream.write(b);
          }

          @Override
          public boolean isReady() {
            return true;
          }

          @Override
          public void setWriteListener(jakarta.servlet.WriteListener writeListener) {}
        };

    when(response.getOutputStream()).thenReturn(servletOutputStream);

    handler.handleDownload(request, response, stored.getId());

    verify(response).setContentType("application/octet-stream");
  }

  // Helper class for mocking ServletOutputStream
  static class ByteArrayOutputStream extends java.io.ByteArrayOutputStream {}
}
