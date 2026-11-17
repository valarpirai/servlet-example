package com.example.servlet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RouterServletTest {

  @Test
  void unexpectedFailureReturnsJson500() throws Exception {
    RouterServlet servlet = new RouterServlet();
    // init() needs the routes table; the failure path reads only the request counter.
    var counter = RouterServlet.class.getDeclaredField("requestCount");
    counter.setAccessible(true);
    counter.set(servlet, new AtomicLong());
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getPathInfo()).thenThrow(new IllegalStateException("boom"));
    when(request.getMethod()).thenReturn("GET");
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.service(request, response);

    verify(response).reset();
    verify(response).setStatus(500);
    assertTrue(body.toString().contains("Internal Server Error"));
    assertFalse(body.toString().contains("boom"));
  }
}
