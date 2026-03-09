package com.example.extlib;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtLibManager {

  private static final Logger logger = LoggerFactory.getLogger(ExtLibManager.class);
  private static final ExtLibManager INSTANCE = new ExtLibManager();
  private static final String EXTLIB_DIR = "extlib";
  private static final String MAVEN_BASE = "https://repo1.maven.org/maven2/";

  private final Set<String> loadedDrivers = ConcurrentHashMap.newKeySet();

  public record DriverInfo(String jarPath, String driverClass) {}

  private static final Map<String, DriverInfo> CATALOGUE =
      Map.of(
          "mysql",
          new DriverInfo(
              "com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar",
              "com.mysql.cj.jdbc.Driver"),
          "postgresql",
          new DriverInfo(
              "org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar", "org.postgresql.Driver"),
          "snowflake",
          new DriverInfo(
              "net/snowflake/snowflake-jdbc/3.15.0/snowflake-jdbc-3.15.0.jar",
              "net.snowflake.client.jdbc.SnowflakeDriver"));

  private ExtLibManager() {
    new File(EXTLIB_DIR).mkdirs();
  }

  public static ExtLibManager getInstance() {
    return INSTANCE;
  }

  public boolean isSupported(String dbType) {
    return CATALOGUE.containsKey(dbType.toLowerCase());
  }

  public boolean isDownloaded(String dbType) {
    DriverInfo info = CATALOGUE.get(dbType.toLowerCase());
    if (info == null) return false;
    String fileName = info.jarPath().substring(info.jarPath().lastIndexOf('/') + 1);
    return new File(EXTLIB_DIR, fileName).exists();
  }

  /**
   * Downloads the JDBC driver JAR for dbType (if not already present), loads it into a
   * URLClassLoader, and registers it with DriverManager. Safe to call multiple times — skips if
   * already loaded.
   */
  public synchronized void downloadAndLoad(String dbType) throws Exception {
    String key = dbType.toLowerCase();
    DriverInfo info = CATALOGUE.get(key);
    if (info == null) throw new IllegalArgumentException("Unknown dbType: " + dbType);

    String fileName = info.jarPath().substring(info.jarPath().lastIndexOf('/') + 1);
    File jarFile = new File(EXTLIB_DIR, fileName);

    if (!jarFile.exists()) {
      String downloadUrl = MAVEN_BASE + info.jarPath();
      logger.info("Downloading {} from {}", fileName, downloadUrl);
      downloadFile(downloadUrl, jarFile);
      logger.info("Downloaded {} ({} bytes)", fileName, jarFile.length());
    } else {
      logger.info("JAR already present: {}", jarFile.getAbsolutePath());
    }

    // Both the contains-check and loadDriver call execute under this synchronized block,
    // keeping the check-then-act atomic. Do not move either outside the lock.
    if (!loadedDrivers.contains(info.driverClass())) {
      loadDriver(jarFile, info.driverClass());
    }
  }

  private void downloadFile(String urlStr, File dest) throws IOException {
    File tmp = new File(dest.getParent(), dest.getName() + ".tmp");
    try (InputStream in = URI.create(urlStr).toURL().openStream();
        OutputStream out = new FileOutputStream(tmp)) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    } catch (IOException e) {
      tmp.delete();
      throw e;
    }
    if (!tmp.renameTo(dest)) {
      tmp.delete();
      throw new IOException("Could not rename " + tmp + " to " + dest);
    }
  }

  private void loadDriver(File jarFile, String driverClass) throws Exception {
    URLClassLoader loader =
        new URLClassLoader(
            new URL[] {jarFile.toURI().toURL()}, Thread.currentThread().getContextClassLoader());
    Driver driver =
        (Driver) Class.forName(driverClass, true, loader).getDeclaredConstructor().newInstance();
    DriverManager.registerDriver(new DriverShim(driver));
    loadedDrivers.add(driverClass);
    logger.info("Registered JDBC driver: {}", driverClass);
  }

  /**
   * Opens a JDBC connection. Caller must close it (managed by SessionManager). extraProps:
   * additional JDBC properties (e.g. warehouse, db, schema for Snowflake).
   */
  public Connection connect(
      String dbType, String url, String user, String password, Map<String, String> extraProps)
      throws Exception {
    downloadAndLoad(dbType);

    Properties props = new Properties();
    props.setProperty("user", user);
    props.setProperty("password", password);
    if (extraProps != null) props.putAll(extraProps);

    return DriverManager.getConnection(url, props);
  }
}
