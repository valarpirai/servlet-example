package com.example.servlet.util;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Servlet filter that adds correlation IDs to every request for distributed tracing. Uses SLF4J MDC
 * to make correlation ID available to all log statements within the request scope.
 */
public class CorrelationIdFilter implements Filter {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  public static final String CORRELATION_ID_MDC_KEY = "correlationId";
  public static final String REQUEST_ID_MDC_KEY = "requestId";
  public static final String REQUEST_METHOD_MDC_KEY = "method";
  public static final String REQUEST_PATH_MDC_KEY = "path";
  public static final String REQUEST_IP_MDC_KEY = "clientIp";

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    // No initialization needed
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    try {
      // Get or generate correlation ID
      String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
      if (correlationId == null || correlationId.trim().isEmpty()) {
        correlationId = generateCorrelationId();
      }

      // Generate unique request ID
      String requestId = generateRequestId();

      // Add to MDC for logging
      MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
      MDC.put(REQUEST_ID_MDC_KEY, requestId);
      MDC.put(REQUEST_METHOD_MDC_KEY, httpRequest.getMethod());
      MDC.put(REQUEST_PATH_MDC_KEY, getFullPath(httpRequest));
      MDC.put(REQUEST_IP_MDC_KEY, getClientIp(httpRequest));

      // Add correlation ID to response headers
      httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
      httpResponse.setHeader("X-Request-ID", requestId);

      // Continue with the request
      chain.doFilter(request, response);

    } finally {
      // Clean up MDC to prevent memory leaks
      MDC.clear();
    }
  }

  @Override
  public void destroy() {
    // No cleanup needed
  }

  /**
   * Generate a new correlation ID using UUID.
   *
   * @return Correlation ID string
   */
  private String generateCorrelationId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Generate a unique request ID (shorter than correlation ID).
   *
   * @return Request ID string
   */
  private String generateRequestId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  /**
   * Get full request path including query string.
   *
   * @param request HTTP request
   * @return Full path with query string
   */
  private String getFullPath(HttpServletRequest request) {
    String path = request.getPathInfo();
    if (path == null) {
      path = request.getRequestURI();
    }
    String queryString = request.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      path = path + "?" + queryString;
    }
    return path;
  }

  /**
   * Get client IP address, checking for proxies.
   *
   * @param request HTTP request
   * @return Client IP address
   */
  private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("X-Real-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getRemoteAddr();
    }
    // If multiple IPs (proxies), take the first one
    if (ip != null && ip.contains(",")) {
      ip = ip.split(",")[0].trim();
    }
    return ip;
  }
}
