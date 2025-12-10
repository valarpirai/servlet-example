package com.example.servlet.processor;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public interface RequestProcessor {

    /**
     * Check if this processor supports the given content type
     *
     * @param contentType The content type to check
     * @return true if this processor can handle the content type
     */
    boolean supports(String contentType);

    /**
     * Process the HTTP request and return a response
     *
     * @param request The HTTP servlet request
     * @return ProcessorResponse containing status, body, and headers
     * @throws IOException if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    ProcessorResponse process(HttpServletRequest request) throws IOException, ServletException;

    /**
     * Get the primary content type this processor handles
     *
     * @return The content type string (e.g., "application/json")
     */
    String getContentType();
}
