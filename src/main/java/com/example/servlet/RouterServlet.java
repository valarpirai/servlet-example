package com.example.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicLong;

public class RouterServlet extends HttpServlet {

    private AtomicLong requestCount;
    private long startTime;

    @Override
    public void init() throws ServletException {
        super.init();
        startTime = System.currentTimeMillis();
        requestCount = new AtomicLong(0);
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
                "\"endpoints\":[\"/health\",\"/metrics\"]," +
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
