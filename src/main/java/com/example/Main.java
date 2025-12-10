package com.example;

import com.example.servlet.RouterServlet;
import com.example.servlet.util.LoggingConfig;
import com.example.servlet.util.PropertiesUtil;
import jakarta.servlet.MultipartConfigElement;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.BindException;

public class Main {

    private static final int PORT = PropertiesUtil.getInt("server.port", 8080);
    private static final Logger logger;

    // Static initializer to configure logging before logger creation
    static {
        LoggingConfig.initialize();
        logger = LoggerFactory.getLogger(Main.class);
    }

    public static void main(String[] args) {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(PORT);
        tomcat.getConnector();

        String contextPath = "";
        String docBase = new File(".").getAbsolutePath();

        Context context = tomcat.addContext(contextPath, docBase);

        String servletName = "RouterServlet";
        String urlPattern = "/*";

        Wrapper servletWrapper = tomcat.addServlet(contextPath, servletName, new RouterServlet());

        // Configure multipart for file uploads from properties
        String location = PropertiesUtil.getString("upload.tempDirectory", System.getProperty("java.io.tmpdir"));
        long maxFileSize = PropertiesUtil.getLong("upload.maxFileSize", 10485760L);
        long maxRequestSize = PropertiesUtil.getLong("upload.maxRequestSize", 52428800L);
        int fileSizeThreshold = PropertiesUtil.getInt("upload.fileSizeThreshold", 1048576);

        MultipartConfigElement multipartConfig = new MultipartConfigElement(
                location,
                maxFileSize,
                maxRequestSize,
                fileSizeThreshold
        );
        servletWrapper.setMultipartConfigElement(multipartConfig);

        context.addServletMappingDecoded(urlPattern, servletName);

        try {
            tomcat.start();

            logger.info("=========================================");
            logger.info("Tomcat server started successfully!");
            logger.info("Port: {}", PORT);
            logger.info("GET Endpoints:");
            logger.info("  - http://localhost:{}/", PORT);
            logger.info("  - http://localhost:{}/health", PORT);
            logger.info("  - http://localhost:{}/metrics", PORT);
            logger.info("  - http://localhost:{}/script-editor (JavaScript Code Editor)", PORT);
            logger.info("POST Endpoints:");
            logger.info("  - http://localhost:{}/api/form   (Content-Type: application/x-www-form-urlencoded)", PORT);
            logger.info("  - http://localhost:{}/api/json   (Content-Type: application/json)", PORT);
            logger.info("  - http://localhost:{}/api/upload (Content-Type: multipart/form-data)", PORT);
            logger.info("  - http://localhost:{}/api/script (Content-Type: application/javascript)", PORT);
            logger.info("  - http://localhost:{}/api/render (Content-Type: text/html)", PORT);
            logger.info("=========================================");

            tomcat.getServer().await();
        } catch (LifecycleException e) {
            // Check if the cause is a BindException (Address already in use)
            Throwable cause = e.getCause();
            if (cause instanceof BindException ||
                (cause != null && cause.getMessage() != null && cause.getMessage().contains("Address already in use"))) {
                logger.error("=========================================");
                logger.error("ERROR: Cannot start server");
                logger.error("Port {} is already in use.", PORT);
                logger.error("To fix this:");
                logger.error("  1. Stop the process using port {}", PORT);
                logger.error("  2. Or use a different port: SERVER_PORT=9090 mvn -PappRun");
                logger.error("=========================================");
                System.exit(1);
            } else {
                // For other LifecycleException errors, log the error and exit
                logger.error("=========================================");
                logger.error("ERROR: Failed to start server");
                logger.error("Error: {}", e.getMessage());
                logger.error("=========================================", e);
                System.exit(1);
            }
        }
    }
}
