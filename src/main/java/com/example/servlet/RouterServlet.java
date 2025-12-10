package com.example.servlet;

import com.example.servlet.processor.FileUploadProcessor;
import com.example.servlet.processor.FormDataProcessor;
import com.example.servlet.processor.JsonDataProcessor;
import com.example.servlet.processor.ProcessorRegistry;
import com.example.servlet.processor.ProcessorResponse;
import com.example.servlet.processor.RequestProcessor;
import com.example.servlet.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class RouterServlet extends HttpServlet {

    private AtomicLong requestCount;
    private long startTime;

    @Override
    public void init() throws ServletException {
        super.init();
        startTime = System.currentTimeMillis();
        requestCount = new AtomicLong(0);

        // Register request processors
        ProcessorRegistry registry = ProcessorRegistry.getInstance();
        registry.register(new FormDataProcessor());
        registry.register(new JsonDataProcessor());
        registry.register(new FileUploadProcessor());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        requestCount.incrementAndGet();

        String path = request.getPathInfo();
        if (path == null) {
            path = "/";
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        switch (path) {
            case "/health":
                handleHealth(out);
                break;
            case "/metrics":
                handleMetrics(out);
                break;
            case "/":
                handleRoot(response, out);
                break;
            default:
                handleNotFound(response, out, path);
                break;
        }

        out.flush();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        requestCount.incrementAndGet();

        String path = request.getPathInfo();
        if (path == null) {
            path = "/";
        }

        // Set default response content type
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Route based on path for POST requests
        switch (path) {
            case "/api/form":
            case "/api/json":
            case "/api/upload":
                handleProcessorRequest(request, response);
                break;
            default:
                PrintWriter out = response.getWriter();
                handleNotFound(response, out, path);
                out.flush();
                break;
        }
    }

    private void handleProcessorRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        String contentType = request.getContentType();

        if (contentType == null || contentType.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            PrintWriter out = response.getWriter();
            out.print(JsonUtil.errorResponse(
                    "Bad Request",
                    "Content-Type header is required",
                    HttpServletResponse.SC_BAD_REQUEST));
            out.flush();
            return;
        }

        // Get processor from registry
        ProcessorRegistry registry = ProcessorRegistry.getInstance();
        RequestProcessor processor = registry.getProcessor(contentType);

        if (processor == null) {
            response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            PrintWriter out = response.getWriter();
            out.print(JsonUtil.errorResponse(
                    "Unsupported Media Type",
                    "No processor found for content type: " + contentType,
                    HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE));
            out.flush();
            return;
        }

        // Process the request
        ProcessorResponse processorResponse = processor.process(request);

        // Write response
        response.setStatus(processorResponse.getStatusCode());
        response.setContentType(processorResponse.getContentType());

        // Set custom headers if any
        for (Map.Entry<String, String> header : processorResponse.getHeaders().entrySet()) {
            response.setHeader(header.getKey(), header.getValue());
        }

        PrintWriter out = response.getWriter();
        out.print(processorResponse.getBody());
        out.flush();
    }

    private void handleHealth(PrintWriter out) {
        long uptime = System.currentTimeMillis() - startTime;

        String healthStatus = String.format(
            "{\"status\":\"UP\",\"timestamp\":%d,\"uptime\":\"%d ms\"}",
            System.currentTimeMillis(),
            uptime
        );

        out.print(healthStatus);
    }

    private void handleMetrics(PrintWriter out) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        String metrics = String.format(
            "{\"metrics\":{" +
                "\"totalRequests\":%d," +
                "\"memory\":{" +
                    "\"used\":%d," +
                    "\"free\":%d," +
                    "\"total\":%d," +
                    "\"max\":%d" +
                "}," +
                "\"threads\":{" +
                    "\"active\":%d" +
                "}," +
                "\"timestamp\":%d" +
            "}}",
            requestCount.get(),
            usedMemory,
            freeMemory,
            totalMemory,
            maxMemory,
            Thread.activeCount(),
            System.currentTimeMillis()
        );

        out.print(metrics);
    }

    private void handleRoot(HttpServletResponse response, PrintWriter out) {
        String welcomeMessage = String.format(
            "{\"message\":\"Welcome to Jakarta EE Servlet Application\"," +
                "\"version\":\"1.0\"," +
                "\"endpoints\":{" +
                    "\"GET\":[\"/\",\"/health\",\"/metrics\"]," +
                    "\"POST\":[\"/api/form\",\"/api/json\",\"/api/upload\"]" +
                "}," +
                "\"timestamp\":%d}",
            System.currentTimeMillis()
        );

        out.print(welcomeMessage);
    }

    private void handleNotFound(HttpServletResponse response, PrintWriter out, String path) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);

        String errorMessage = String.format(
            "{\"error\":\"Not Found\",\"path\":\"%s\",\"status\":404}",
            path
        );

        out.print(errorMessage);
    }
}
