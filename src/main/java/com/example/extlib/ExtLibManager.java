package com.example.extlib;

import com.example.datasource.DataSourceRegistry;
import com.example.datasource.DataSourceStrategy;
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

  // Maps driver class name → its URLClassLoader, kept open for the driver's lifetime.
  // Stored so loaders can be closed at JVM shutdown via shutdown().
  private final Map<String, URLClassLoader> loadedDrivers = new ConcurrentHashMap<>();

  private ExtLibManager() {
    new File(EXTLIB_DIR).mkdirs();
  }

  public static ExtLibManager getInstance() {
    return INSTANCE;
  }

  public boolean isSupported(String dbType) {
    return DataSourceRegistry.getInstance().isSupported(dbType);
  }

  public boolean isDownloaded(String dbType) {
    DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(dbType);
    if (strategy == null) return false;
    return jarFile(strategy).exists();
  }

  /**
   * Downloads the JDBC driver JAR (if not already present), loads it into a URLClassLoader, and
   * registers it with DriverManager. Safe to call multiple times.
   */
  public synchronized void downloadAndLoad(String dbType) throws Exception {
    DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(dbType);
    if (strategy == null) throw new IllegalArgumentException("Unknown dbType: " + dbType);

    File jar = jarFile(strategy);

    if (!jar.exists()) {
      String downloadUrl = MAVEN_BASE + strategy.getJarPath();
      logger.info("Downloading {} from {}", jar.getName(), downloadUrl);
      downloadFile(downloadUrl, jar);
      logger.info("Downloaded {} ({} bytes)", jar.getName(), jar.length());
    } else {
      logger.info("JAR already present: {}", jar.getAbsolutePath());
    }

    // Both the contains-check and loadDriver call execute under this synchronized block,
    // keeping the check-then-act atomic. Do not move either outside the lock.
    if (!loadedDrivers.containsKey(strategy.getDriverClass())) {
      loadDriver(jar, strategy.getDriverClass());
    }
  }

  /** Opens a JDBC connection. Caller must close it (managed by SessionManager). */
  public Connection connect(
      String dbType, String url, String user, String password, Map<String, String> extraProps)
      throws Exception {
    downloadAndLoad(dbType);
    // Strategy is guaranteed non-null here — downloadAndLoad throws if unknown
    DataSourceStrategy strategy = DataSourceRegistry.getInstance().get(dbType);
    Properties props = strategy.buildConnectionProperties(user, password, extraProps);
    return DriverManager.getConnection(url, props);
  }

  private File jarFile(DataSourceStrategy strategy) {
    String jarPath = strategy.getJarPath();
    String fileName = jarPath.substring(jarPath.lastIndexOf('/') + 1);
    return new File(EXTLIB_DIR, fileName);
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
    loadedDrivers.put(driverClass, loader);
    logger.info("Registered JDBC driver: {}", driverClass);
  }

  /** Closes all URLClassLoaders. Call during application shutdown. */
  public void shutdown() {
    loadedDrivers.forEach(
        (driverClass, loader) -> {
          try {
            loader.close();
          } catch (IOException e) {
            logger.warn("Failed to close URLClassLoader for {}", driverClass, e);
          }
        });
    loadedDrivers.clear();
  }
}
