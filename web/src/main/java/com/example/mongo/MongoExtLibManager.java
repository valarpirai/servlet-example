package com.example.mongo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads the MongoDB Java driver (and its transitive runtime deps) into data/extlib/ and loads
 * them into a URLClassLoader, mirroring how JDBC drivers are loaded via ExtLibManager. Unlike JDBC,
 * MongoDB's client API has no JDK-standard interface to cast against, so callers must drive the
 * loaded classes purely via reflection (see MongoReflectionClient).
 */
public class MongoExtLibManager {

  private static final Logger logger = LoggerFactory.getLogger(MongoExtLibManager.class);
  private static final MongoExtLibManager INSTANCE = new MongoExtLibManager();
  private static final String EXTLIB_DIR = "data/extlib";
  private static final String MAVEN_BASE = "https://repo1.maven.org/maven2/";
  private static final String DRIVER_VERSION = "5.2.1";

  // groupId/artifactId pairs, in dependency order (bson has no deps, core depends on bson,
  // sync depends on core) - order doesn't affect the classloader but keeps download logs readable.
  private static final String[][] ARTIFACTS = {
    {"org/mongodb", "bson"},
    {"org/mongodb", "mongodb-driver-core"},
    {"org/mongodb", "mongodb-driver-sync"},
  };

  private volatile URLClassLoader loader;

  private MongoExtLibManager() {
    new File(EXTLIB_DIR).mkdirs();
  }

  public static MongoExtLibManager getInstance() {
    return INSTANCE;
  }

  public boolean isDownloaded() {
    for (String[] artifact : ARTIFACTS) {
      if (!jarFile(artifact[1]).exists()) return false;
    }
    return true;
  }

  /**
   * Downloads the driver jars (if not already present) and returns a shared URLClassLoader over all
   * of them. Safe to call multiple times.
   */
  public synchronized ClassLoader downloadAndLoad() throws IOException {
    if (loader != null) return loader;

    URL[] urls = new URL[ARTIFACTS.length];
    for (int i = 0; i < ARTIFACTS.length; i++) {
      String[] artifact = ARTIFACTS[i];
      File jar = jarFile(artifact[1]);
      if (!jar.exists()) {
        String downloadUrl = jarPath(artifact[0], artifact[1]);
        logger.info("Downloading {} from {}", jar.getName(), MAVEN_BASE + downloadUrl);
        downloadFile(MAVEN_BASE + downloadUrl, jar);
        logger.info("Downloaded {} ({} bytes)", jar.getName(), jar.length());
      } else {
        logger.info("JAR already present: {}", jar.getAbsolutePath());
      }
      urls[i] = jar.toURI().toURL();
    }

    loader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    logger.info("Loaded MongoDB driver classes from data/extlib/");
    return loader;
  }

  private String jarPath(String groupPath, String artifactId) {
    return groupPath
        + "/"
        + artifactId
        + "/"
        + DRIVER_VERSION
        + "/"
        + artifactId
        + "-"
        + DRIVER_VERSION
        + ".jar";
  }

  private File jarFile(String artifactId) {
    return new File(EXTLIB_DIR, artifactId + "-" + DRIVER_VERSION + ".jar");
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

  /** Closes the shared URLClassLoader. Call during application shutdown. */
  public synchronized void shutdown() {
    if (loader != null) {
      try {
        loader.close();
      } catch (IOException e) {
        logger.warn("Failed to close MongoDB driver URLClassLoader", e);
      }
      loader = null;
    }
  }
}
