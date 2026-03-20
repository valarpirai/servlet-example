package com.example.servlet.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.servlet.model.ProcessorResponse;
import com.example.servlet.storage.AttachmentManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class FileUploadProcessorTest {

  @TempDir Path tempDir;

  @Mock private HttpServletRequest request;
  @Mock private Part filePart;

  private FileUploadProcessor processor;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    System.setProperty("storage.filesystem.path", tempDir.toString());
    System.setProperty("storage.type", "filesystem");

    // Clean up and reset using helper
    com.example.servlet.storage.StorageTestHelper.cleanupAttachmentsDirectory();
    com.example.servlet.storage.StorageTestHelper.resetSingleton(AttachmentManager.class);

    processor = new FileUploadProcessor();
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("storage.filesystem.path");
    System.clearProperty("storage.type");
  }

  @Test
  void testSupportsMultipartFormData() {
    assertTrue(processor.supports("multipart/form-data"));
    assertTrue(processor.supports("multipart/form-data; boundary=----WebKitFormBoundary"));
    assertTrue(processor.supports("MULTIPART/FORM-DATA"));
  }

  @Test
  void testDoesNotSupportOtherContentTypes() {
    assertFalse(processor.supports("application/json"));
    assertFalse(processor.supports("text/plain"));
    assertFalse(processor.supports(null));
  }

  @Test
  void testGetContentType() {
    assertEquals("multipart/form-data", processor.getContentType());
  }

  @Test
  void testProcessNoMultipartData() throws Exception {
    when(request.getParts()).thenReturn(new ArrayList<>());

    ProcessorResponse response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(response.getBody().contains("No multipart data provided"));
  }

  @Test
  void testProcessFileUpload() throws Exception {
    byte[] fileContent = "test content".getBytes();

    when(filePart.getSubmittedFileName()).thenReturn("test.txt");
    when(filePart.getContentType()).thenReturn("text/plain");
    when(filePart.getSize()).thenReturn((long) fileContent.length);
    when(filePart.getName()).thenReturn("file");
    when(filePart.getInputStream()).thenReturn(new ByteArrayInputStream(fileContent));

    Collection<Part> parts = new ArrayList<>();
    parts.add(filePart);
    when(request.getParts()).thenReturn(parts);

    ProcessorResponse response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("attachmentId"));
    assertTrue(response.getBody().contains("test.txt"));
    assertTrue(response.getBody().contains("downloadUrl"));
    assertTrue(response.getBody().contains("hash"));
  }

  @Test
  void testProcessFileTooLarge() throws Exception {
    long largeSize = 600L * 1024L * 1024L; // 600MB (exceeds default 500MB limit)

    when(filePart.getSubmittedFileName()).thenReturn("large.bin");
    when(filePart.getContentType()).thenReturn("application/octet-stream");
    when(filePart.getSize()).thenReturn(largeSize);
    when(filePart.getName()).thenReturn("file");

    Collection<Part> parts = new ArrayList<>();
    parts.add(filePart);
    when(request.getParts()).thenReturn(parts);

    ProcessorResponse response = processor.process(request);

    assertEquals(413, response.getStatusCode());
    assertTrue(response.getBody().contains("File size exceeds maximum limit"));
  }

  @Test
  void testProcessMultipleFiles() throws Exception {
    Part filePart1 = mock(Part.class);
    Part filePart2 = mock(Part.class);

    when(filePart1.getSubmittedFileName()).thenReturn("file1.txt");
    when(filePart1.getContentType()).thenReturn("text/plain");
    when(filePart1.getSize()).thenReturn(100L);
    when(filePart1.getName()).thenReturn("file1");
    when(filePart1.getInputStream()).thenReturn(new ByteArrayInputStream("content1".getBytes()));

    when(filePart2.getSubmittedFileName()).thenReturn("file2.txt");
    when(filePart2.getContentType()).thenReturn("text/plain");
    when(filePart2.getSize()).thenReturn(100L);
    when(filePart2.getName()).thenReturn("file2");
    when(filePart2.getInputStream()).thenReturn(new ByteArrayInputStream("content2".getBytes()));

    Collection<Part> parts = new ArrayList<>();
    parts.add(filePart1);
    parts.add(filePart2);
    when(request.getParts()).thenReturn(parts);

    ProcessorResponse response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("file1.txt"));
    assertTrue(response.getBody().contains("file2.txt"));
    assertTrue(response.getBody().contains("\"fileCount\": 2"));
  }

  @Test
  void testProcessWithFormFields() throws Exception {
    Part formPart = mock(Part.class);

    when(filePart.getSubmittedFileName()).thenReturn("test.txt");
    when(filePart.getContentType()).thenReturn("text/plain");
    when(filePart.getSize()).thenReturn(100L);
    when(filePart.getName()).thenReturn("file");
    when(filePart.getInputStream()).thenReturn(new ByteArrayInputStream("content".getBytes()));

    when(formPart.getSubmittedFileName()).thenReturn(null);
    when(formPart.getName()).thenReturn("description");
    when(request.getParameter("description")).thenReturn("Test description");

    Collection<Part> parts = new ArrayList<>();
    parts.add(filePart);
    parts.add(formPart);
    when(request.getParts()).thenReturn(parts);

    ProcessorResponse response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("test.txt"));
    assertTrue(response.getBody().contains("fields"));
  }

  @Test
  void testProcessIllegalStateException() throws Exception {
    when(request.getParts()).thenThrow(new IllegalStateException("Not a multipart request"));

    ProcessorResponse response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(response.getBody().contains("Request is not a valid multipart request"));
  }

  @Test
  void testProcessGeneralException() throws Exception {
    when(request.getParts()).thenThrow(new RuntimeException("Test error"));

    ProcessorResponse response = processor.process(request);

    assertEquals(500, response.getStatusCode());
    assertTrue(response.getBody().contains("Error processing file upload"));
  }
}
