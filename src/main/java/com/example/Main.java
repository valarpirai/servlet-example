package com.example;

import com.example.config.DbPropertiesLoader;
import com.example.servlet.RouterServlet;
import com.example.util.CorrelationIdFilter;
import com.example.util.LoggingConfig;
import com.example.util.PropertiesUtil;
import jakarta.servlet.MultipartConfigElement;
import java.io.File;
import java.net.BindException;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final int DEFAULT_PORT = 8080;
  private static final Logger logger;

  static {
    LoggingConfig.initialize();
    DbPropertiesLoader.initialize(); // wires LRU-backed PropertyRepository into PropertiesUtil
    logger = LoggerFactory.getLogger(Main.class);
  }

  /** Priority: -Dserver.port → SERVER_PORT env var → app_properties table → DEFAULT_PORT. */
  private static int resolvePort() {
    String sysProp = System.getProperty("server.port");
    if (sysProp != null && !sysProp.isBlank()) {
      try {
        return Integer.parseInt(sysProp.trim());
      } catch (NumberFormatException e) {
        logger.warn("Invalid -Dserver.port '{}', trying SERVER_PORT env var", sysProp);
      }
    }
    String env = System.getenv("SERVER_PORT");
    if (env != null && !env.isBlank()) {
      try {
        return Integer.parseInt(env.trim());
      } catch (NumberFormatException e) {
        logger.warn("Invalid SERVER_PORT env var '{}', falling back to app_properties", env);
      }
    }
    return PropertiesUtil.getInt("server.port", DEFAULT_PORT);
  }

  public static void main(String[] args) {
    int port = resolvePort();
    Tomcat tomcat = new Tomcat();
    tomcat.setPort(port);
    tomcat.getConnector();

    String contextPath = "";
    String docBase = new File(".").getAbsolutePath();

    Context context = tomcat.addContext(contextPath, docBase);

    String servletName = "RouterServlet";
    String urlPattern = "/*";

    Wrapper servletWrapper = tomcat.addServlet(contextPath, servletName, new RouterServlet());

    // Configure multipart for file uploads from properties
    String location =
        PropertiesUtil.getString("upload.tempDirectory", System.getProperty("java.io.tmpdir"));
    long maxFileSize = PropertiesUtil.getLong("upload.maxFileSize", 10485760L);
    long maxRequestSize = PropertiesUtil.getLong("upload.maxRequestSize", 52428800L);
    int fileSizeThreshold = PropertiesUtil.getInt("upload.fileSizeThreshold", 1048576);

    MultipartConfigElement multipartConfig =
        new MultipartConfigElement(location, maxFileSize, maxRequestSize, fileSizeThreshold);
    servletWrapper.setMultipartConfigElement(multipartConfig);

    context.addServletMappingDecoded(urlPattern, servletName);

    // Add CorrelationIdFilter for structured logging
    String filterName = "CorrelationIdFilter";
    FilterDef filterDef = new FilterDef();
    filterDef.setFilterName(filterName);
    filterDef.setFilter(new CorrelationIdFilter());
    context.addFilterDef(filterDef);

    FilterMap filterMap = new FilterMap();
    filterMap.setFilterName(filterName);
    filterMap.addURLPattern("/*");
    context.addFilterMap(filterMap);

    try {
      tomcat.start();

      logger.info("=========================================");
      logger.info("Tomcat server started successfully!");
      logger.info("Port: {}", port);
      logger.info("GET Endpoints:");
      logger.info("  - http://localhost:{}/", port);
      logger.info("  - http://localhost:{}/health", port);
      logger.info("  - http://localhost:{}/metrics", port);
      logger.info("  - http://localhost:{}/script-editor (JavaScript Code Editor)", port);
      logger.info("  - http://localhost:{}/data-browser (Database Browser)", port);
      logger.info("POST Endpoints:");
      logger.info(
          "  - http://localhost:{}/api/form   (Content-Type: application/x-www-form-urlencoded)",
          port);
      logger.info("  - http://localhost:{}/api/json   (Content-Type: application/json)", port);
      logger.info("  - http://localhost:{}/api/upload (Content-Type: multipart/form-data)", port);
      logger.info(
          "  - http://localhost:{}/api/script (Content-Type: application/javascript)", port);
      logger.info("  - http://localhost:{}/api/render (Content-Type: text/html)", port);
      logger.info("=========================================");

      tomcat.getServer().await();
    } catch (LifecycleException e) {
      // Check if the cause is a BindException (Address already in use)
      Throwable cause = e.getCause();
      if (cause instanceof BindException
          || (cause != null
              && cause.getMessage() != null
              && cause.getMessage().contains("Address already in use"))) {
        logger.error("=========================================");
        logger.error("ERROR: Cannot start server");
        logger.error("Port {} is already in use.", port);
        logger.error("To fix this:");
        logger.error("  1. Stop the process using port {}", port);
        logger.error("  2. Or use a different port: SERVER_PORT=9090 mvn -PappRun");
        logger.error("     or: mvn -PappRun -Dserver.port=9090");
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
