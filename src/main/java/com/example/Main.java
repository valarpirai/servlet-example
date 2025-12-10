package com.example;

import com.example.servlet.RouterServlet;
import com.example.servlet.util.PropertiesUtil;
import jakarta.servlet.MultipartConfigElement;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

import java.io.File;

public class Main {

    private static final int PORT = PropertiesUtil.getInt("server.port", 8080);

    public static void main(String[] args) throws LifecycleException {
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

        tomcat.start();

        System.out.println("=========================================");
        System.out.println("Tomcat server started successfully!");
        System.out.println("Port: " + PORT);
        System.out.println("\nGET Endpoints:");
        System.out.println("  - http://localhost:" + PORT + "/");
        System.out.println("  - http://localhost:" + PORT + "/health");
        System.out.println("  - http://localhost:" + PORT + "/metrics");
        System.out.println("  - http://localhost:" + PORT + "/script-editor (JavaScript Code Editor)");
        System.out.println("\nPOST Endpoints:");
        System.out.println("  - http://localhost:" + PORT + "/api/form   (Content-Type: application/x-www-form-urlencoded)");
        System.out.println("  - http://localhost:" + PORT + "/api/json   (Content-Type: application/json)");
        System.out.println("  - http://localhost:" + PORT + "/api/upload (Content-Type: multipart/form-data)");
        System.out.println("  - http://localhost:" + PORT + "/api/script (Content-Type: application/javascript)");
        System.out.println("  - http://localhost:" + PORT + "/api/render (Content-Type: text/html)");
        System.out.println("=========================================");

        tomcat.getServer().await();
    }
}
