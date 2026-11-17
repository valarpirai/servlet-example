package com.example.processor;

import com.example.model.ProcessorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public interface IRequestProcessor {

  /**
   * Process the HTTP request and return a response
   *
   * @param request The HTTP servlet request
   * @return ProcessorResponse containing status, body, and headers
   * @throws IOException if an I/O error occurs
   * @throws ServletException if a servlet error occurs
   */
  ProcessorResponse process(HttpServletRequest request) throws IOException, ServletException;
}
