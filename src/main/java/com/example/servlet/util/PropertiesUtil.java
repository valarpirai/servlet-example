package com.example.servlet.util;

import com.example.config.PropertyRepository;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class PropertiesUtil {

  private static Logger logger;
  private static final String CONFIG_FILE = "application.yml";
  private static Map<String, Object> yamlProperties;

  // LRU cache: key → Optional.of(value) for hits, Optional.empty() for DB misses.
  // Capacity 500; backed by access-ordered LinkedHashMap for LRU eviction.
  private static final Map<String, Optional<String>> lruCache =
      Collections.synchronizedMap(
          new LinkedHashMap<String, Optional<String>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Optional<String>> eldest) {
              return size() > 500;
            }
          });

  // Set by DbPropertiesLoader once the DB connection is ready.
  private static volatile PropertyRepository propertyRepository;

  static {
    loadYaml();
  }

  private static Logger getLogger() {
    if (logger == null) {
      logger = LoggerFactory.getLogger(PropertiesUtil.class);
    }
    return logger;
  }

  private static void loadYaml() {
    try (InputStream in = PropertiesUtil.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
      if (in == null) {
        System.err.println("Warning: " + CONFIG_FILE + " not found. Using defaults.");
        yamlProperties = Map.of();
        return;
      }
      Yaml yaml = new Yaml();
      yamlProperties = yaml.load(in);
      if (yamlProperties == null) {
        yamlProperties = Map.of();
      }
    } catch (Exception e) {
      System.err.println("Error loading " + CONFIG_FILE + ": " + e.getMessage());
      yamlProperties = Map.of();
    }
  }

  /**
   * Register the DB-backed property repository. Clears the LRU cache so subsequent reads go to the
   * DB. Pass null to detach (used in tests).
   */
  public static void setPropertyRepository(PropertyRepository repo) {
    propertyRepository = repo;
    lruCache.clear();
  }

  // ---- Public typed accessors ----

  public static String getString(String key, String defaultValue) {
    Object value = getProperty(key);
    return value != null ? String.valueOf(value) : defaultValue;
  }

  public static int getInt(String key, int defaultValue) {
    Object value = getProperty(key);
    if (value == null) return defaultValue;
    try {
      if (value instanceof Number) return ((Number) value).intValue();
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException e) {
      getLogger().warn("Invalid integer value for key '{}': {}", key, value);
      return defaultValue;
    }
  }

  public static long getLong(String key, long defaultValue) {
    Object value = getProperty(key);
    if (value == null) return defaultValue;
    try {
      if (value instanceof Number) return ((Number) value).longValue();
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException e) {
      getLogger().warn("Invalid long value for key '{}': {}", key, value);
      return defaultValue;
    }
  }

  public static boolean getBoolean(String key, boolean defaultValue) {
    Object value = getProperty(key);
    if (value == null) return defaultValue;
    if (value instanceof Boolean) return (Boolean) value;
    return Boolean.parseBoolean(String.valueOf(value));
  }

  public static double getDouble(String key, double defaultValue) {
    Object value = getProperty(key);
    if (value == null) return defaultValue;
    try {
      if (value instanceof Number) return ((Number) value).doubleValue();
      return Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException e) {
      getLogger().warn("Invalid double value for key '{}': {}", key, value);
      return defaultValue;
    }
  }

  public static boolean hasProperty(String key) {
    return getProperty(key) != null;
  }

  /** Reload YAML and clear the LRU cache (next reads re-fetch from DB). */
  public static void reload() {
    loadYaml();
    lruCache.clear();
  }

  /** Returns the YAML properties map (now only contains db.* bootstrap keys). */
  public static Map<String, Object> getAllProperties() {
    return yamlProperties;
  }

  public static String getEnvironment() {
    return getString("application.environment", "dev");
  }

  public static boolean isDevEnvironment() {
    return "dev".equalsIgnoreCase(getEnvironment());
  }

  // ---- Internal lookup: LRU cache → DB → YAML ----

  private static Object getProperty(String key) {
    if (key == null || key.trim().isEmpty()) return null;

    // 1. LRU cache
    Optional<String> cached = lruCache.get(key);
    if (cached != null) {
      // Cache hit: present = DB value found; empty = DB miss, fall through to YAML
      if (cached.isPresent()) return cached.get();
      return getYamlProperty(key);
    }

    // 2. DB lookup (lazy)
    if (propertyRepository != null) {
      try {
        Optional<String> dbValue = propertyRepository.findValueByName(key);
        lruCache.put(key, dbValue);
        if (dbValue.isPresent()) return dbValue.get();
        // DB miss – fall through to YAML
      } catch (Exception e) {
        getLogger().warn("DB lookup failed for key '{}', using YAML fallback", key, e);
      }
    }

    // 3. YAML fallback (only db.* keys live here post-migration)
    return getYamlProperty(key);
  }

  @SuppressWarnings("unchecked")
  private static Object getYamlProperty(String key) {
    if (yamlProperties == null) return null;

    String[] parts = key.split("\\.");
    Map<String, Object> current = yamlProperties;

    for (int i = 0; i < parts.length - 1; i++) {
      Object val = current.get(parts[i]);
      if (!(val instanceof Map)) return null;
      current = (Map<String, Object>) val;
    }

    Object value = current.get(parts[parts.length - 1]);
    if (value instanceof String) {
      value = resolvePlaceholders((String) value);
    }
    return value;
  }

  private static String resolvePlaceholders(String value) {
    if (value == null || !value.contains("${")) return value;

    String result = value;
    int start;
    while ((start = result.indexOf("${")) != -1) {
      int end = result.indexOf("}", start);
      if (end == -1) break;

      String placeholder = result.substring(start + 2, end);
      String varName = placeholder;
      String defaultVal = null;

      int colon = placeholder.indexOf(':');
      if (colon != -1) {
        varName = placeholder.substring(0, colon);
        defaultVal = placeholder.substring(colon + 1);
      }

      String resolved = System.getenv(varName);
      if (resolved == null) resolved = System.getProperty(varName);
      if (resolved == null) resolved = defaultVal;
      if (resolved == null) resolved = "${" + placeholder + "}";

      result = result.substring(0, start) + resolved + result.substring(end + 1);
    }
    return result;
  }
}
