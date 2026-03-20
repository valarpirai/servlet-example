package com.example.servlet.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Tests for CorrelationIdFilter to verify correlation ID and MDC context. */
public class CorrelationIdFilterTest {

  private CorrelationIdFilter filter;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain filterChain;

  @BeforeEach
  public void setUp() {
    filter = new CorrelationIdFilter();
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    filterChain = mock(FilterChain.class);

    // Default request setup
    when(request.getMethod()).thenReturn("GET");
    when(request.getPathInfo()).thenReturn("/test");
    when(request.getRequestURI()).thenReturn("/test");
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
  }

  @AfterEach
  public void tearDown() {
    // Ensure MDC is cleared
    MDC.clear();
  }

  @Test
  public void testGeneratesCorrelationIdWhenNotProvided() throws Exception {
    // Given: No correlation ID header
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // When: Filter processes request
    filter.doFilter(request, response, filterChain);

    // Then: Response header should have correlation ID
    verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), anyString());
    verify(response).setHeader(eq("X-Request-ID"), anyString());
    verify(filterChain).doFilter(request, response);
  }

  @Test
  public void testUsesProvidedCorrelationId() throws Exception {
    // Given: Correlation ID provided in request
    String correlationId = "test-correlation-id";
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(correlationId);

    // When: Filter processes request
    filter.doFilter(request, response, filterChain);

    // Then: Same correlation ID should be set in response
    verify(response).setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  public void testMdcContextSetDuringRequest() throws Exception {
    // Given: Request with no correlation ID
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // When: Filter processes request
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          // Then: MDC should be populated during chain execution
          assertNotNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
          assertNotNull(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY));
          assertEquals("GET", MDC.get(CorrelationIdFilter.REQUEST_METHOD_MDC_KEY));
          assertEquals("/test", MDC.get(CorrelationIdFilter.REQUEST_PATH_MDC_KEY));
          assertEquals("127.0.0.1", MDC.get(CorrelationIdFilter.REQUEST_IP_MDC_KEY));
        });
  }

  @Test
  public void testMdcContextClearedAfterRequest() throws Exception {
    // Given: Request
    when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

    // When: Filter processes request
    filter.doFilter(request, response, filterChain);

    // Then: MDC should be cleared after filter completes
    assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
    assertNull(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY));
  }

  @Test
  public void testHandlesQueryString() throws Exception {
    // Given: Request with query string
    when(request.getPathInfo()).thenReturn("/api/test");
    when(request.getQueryString()).thenReturn("param1=value1&param2=value2");

    // When: Filter processes request
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          // Then: Path should include query string
          String path = MDC.get(CorrelationIdFilter.REQUEST_PATH_MDC_KEY);
          assertTrue(path.contains("param1=value1"));
          assertTrue(path.contains("param2=value2"));
        });
  }

  @Test
  public void testExtractsIpFromXForwardedFor() throws Exception {
    // Given: Request with X-Forwarded-For header
    when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1, 10.0.0.1");
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    // When: Filter processes request
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          // Then: Should use first IP from X-Forwarded-For
          assertEquals("192.168.1.1", MDC.get(CorrelationIdFilter.REQUEST_IP_MDC_KEY));
        });
  }

  @Test
  public void testExtractsIpFromXRealIp() throws Exception {
    // Given: Request with X-Real-IP header
    when(request.getHeader("X-Real-IP")).thenReturn("192.168.1.100");
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    // When: Filter processes request
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          // Then: Should use X-Real-IP
          assertEquals("192.168.1.100", MDC.get(CorrelationIdFilter.REQUEST_IP_MDC_KEY));
        });
  }

  @Test
  public void testFallsBackToRemoteAddr() throws Exception {
    // Given: Request without proxy headers
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    // When: Filter processes request
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          // Then: Should use remote addr
          assertEquals("127.0.0.1", MDC.get(CorrelationIdFilter.REQUEST_IP_MDC_KEY));
        });
  }

  @Test
  public void testMdcClearedEvenOnException() throws Exception {
    // Given: Filter chain throws exception
    doThrow(new RuntimeException("Test exception")).when(filterChain).doFilter(request, response);

    // When: Filter processes request (expect exception)
    try {
      filter.doFilter(request, response, filterChain);
      fail("Should have thrown exception");
    } catch (RuntimeException e) {
      // Expected
    }

    // Then: MDC should still be cleared
    assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
    assertNull(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY));
  }
}
