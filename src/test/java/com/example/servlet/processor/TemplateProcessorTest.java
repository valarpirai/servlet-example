package com.example.servlet.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.servlet.model.ProcessorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TemplateProcessorTest {

  @Mock private HttpServletRequest request;

  private TemplateProcessor processor;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    processor = new TemplateProcessor();
  }

  @Test
  void testSupportsTextHtml() {
    assertTrue(processor.supports("text/html"));
    assertTrue(processor.supports("text/html; charset=UTF-8"));
    assertTrue(processor.supports("TEXT/HTML"));
  }

  @Test
  void testDoesNotSupportOtherContentTypes() {
    assertFalse(processor.supports("application/json"));
    assertFalse(processor.supports("text/plain"));
    assertFalse(processor.supports(null));
  }

  @Test
  void testGetContentType() {
    assertEquals("text/html", processor.getContentType());
  }

  @Test
  void testProcessInlineTemplate() throws Exception {
    String requestBody = "{\"template\": \"<h1>{{title}}</h1>\", \"data\": {\"title\": \"Hello\"}}";

    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

    ProcessorResponse response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertEquals("text/html; charset=UTF-8", response.getContentType());
    assertTrue(response.getBody().contains("<h1>Hello</h1>"));
  }

  @Test
  void testProcessEmptyRequestBody() throws Exception {
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));

    ProcessorResponse response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertEquals("application/json", response.getContentType());
    assertTrue(response.getBody().contains("Empty request body"));
  }

  @Test
  void testProcessInvalidJson() throws Exception {
    String requestBody = "{invalid json}";

    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

    ProcessorResponse response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertEquals("application/json", response.getContentType());
    assertTrue(response.getBody().contains("Invalid JSON"));
  }

  @Test
  void testProcessMissingTemplate() throws Exception {
    String requestBody = "{\"data\": {\"title\": \"Hello\"}}";

    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

    ProcessorResponse response = processor.process(request);

    assertEquals(400, response.getStatusCode());
    assertTrue(response.getBody().contains("Missing 'templatePath' or 'template'"));
  }

  @Test
  void testProcessTemplateWithNoData() throws Exception {
    String requestBody = "{\"template\": \"<h1>Static Content</h1>\"}";

    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

    ProcessorResponse response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("<h1>Static Content</h1>"));
  }

  @Test
  void testProcessTemplateWithNestedData() throws Exception {
    String requestBody =
        "{\"template\": \"<h1>{{user.name}}</h1>\", \"data\": {\"user\": {\"name\":"
            + " \"John\"}}}";

    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

    ProcessorResponse response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("<h1>John</h1>"));
  }

  @Test
  void testProcessTemplateWithForLoop() throws Exception {
    String requestBody =
        "{\"template\": \"{{#for item in items}}<li>{{item}}</li>{{/for}}\", \"data\": "
            + "{\"items\": [\"A\", \"B\", \"C\"]}}";

    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

    ProcessorResponse response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("<li>A</li>"));
    assertTrue(response.getBody().contains("<li>B</li>"));
    assertTrue(response.getBody().contains("<li>C</li>"));
  }

  @Test
  void testProcessTemplateNotFoundByPath() throws Exception {
    String requestBody = "{\"templatePath\": \"nonexistent.html\"}";

    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

    ProcessorResponse response = processor.process(request);

    assertEquals(404, response.getStatusCode());
    assertTrue(response.getBody().contains("Template not found"));
  }

  @Test
  void testProcessTemplateWithXssProtection() throws Exception {
    String requestBody =
        "{\"template\": \"<p>{{content}}</p>\", \"data\": {\"content\":"
            + " \"<script>alert('xss')</script>\"}}";

    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

    ProcessorResponse response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    // Should be escaped
    assertFalse(response.getBody().contains("<script>"));
    assertTrue(response.getBody().contains("&lt;script&gt;"));
  }

  @Test
  void testProcessTemplateWithNumbers() throws Exception {
    String requestBody = "{\"template\": \"<p>{{count}}</p>\", \"data\": {\"count\": 42}}";

    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

    ProcessorResponse response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("<p>42</p>"));
  }

  @Test
  void testProcessTemplateWithBoolean() throws Exception {
    String requestBody = "{\"template\": \"<p>{{active}}</p>\", \"data\": {\"active\": true}}";

    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

    ProcessorResponse response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("<p>true</p>"));
  }

  @Test
  void testProcessTemplateWithNull() throws Exception {
    String requestBody = "{\"template\": \"<p>{{value}}</p>\", \"data\": {\"value\": null}}";

    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

    ProcessorResponse response = processor.process(request);

    assertEquals(200, response.getStatusCode());
    // Null should be handled gracefully
    assertTrue(response.getBody().contains("<p>"));
  }

  @Test
  void testProcessIoException() throws Exception {
    when(request.getReader()).thenThrow(new IOException("Test exception"));

    ProcessorResponse response = processor.process(request);

    assertEquals(500, response.getStatusCode());
    assertTrue(response.getBody().contains("Internal Server Error"));
  }
}
