package com.example;

import com.example.servlet.RouterServlet;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

import java.io.File;

public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws LifecycleException {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(PORT);
        tomcat.getConnector();

        String contextPath = "";
        String docBase = new File(".").getAbsolutePath();

        Context context = tomcat.addContext(contextPath, docBase);

        String servletName = "RouterServlet";
        String urlPattern = "/*";

        tomcat.addServlet(contextPath, servletName, new RouterServlet());
        context.addServletMappingDecoded(urlPattern, servletName);

        tomcat.start();

        System.out.println("=========================================");
        System.out.println("Tomcat server started successfully!");
        System.out.println("Port: " + PORT);
        System.out.println("Endpoints:");
        System.out.println("  - http://localhost:" + PORT + "/");
        System.out.println("  - http://localhost:" + PORT + "/health");
        System.out.println("  - http://localhost:" + PORT + "/metrics");
        System.out.println("=========================================");

        tomcat.getServer().await();
    }
}
