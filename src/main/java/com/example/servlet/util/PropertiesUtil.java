package com.example.servlet.util;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public class PropertiesUtil {

    private static final String CONFIG_FILE = "application.yml";
    private static Map<String, Object> properties;

    static {
        loadProperties();
    }

    /**
     * Load properties from YAML file
     */
    private static void loadProperties() {
        try (InputStream inputStream = PropertiesUtil.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                System.err.println("Warning: " + CONFIG_FILE + " not found. Using default values.");
                properties = Map.of();
                return;
            }

            Yaml yaml = new Yaml();
            properties = yaml.load(inputStream);

            if (properties == null) {
                properties = Map.of();
            }
        } catch (Exception e) {
            System.err.println("Error loading properties file: " + e.getMessage());
            properties = Map.of();
        }
    }

    /**
     * Get a property value as String
     *
     * @param key The property key in dot notation (e.g., "server.port")
     * @param defaultValue The default value if property not found
     * @return The property value or default value
     */
    public static String getString(String key, String defaultValue) {
        Object value = getProperty(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    /**
     * Get a property value as Integer
     *
     * @param key The property key in dot notation (e.g., "server.port")
     * @param defaultValue The default value if property not found
     * @return The property value or default value
     */
    public static int getInt(String key, int defaultValue) {
        Object value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid integer value for key '" + key + "': " + value);
            return defaultValue;
        }
    }

    /**
     * Get a property value as Long
     *
     * @param key The property key in dot notation (e.g., "upload.maxFileSize")
     * @param defaultValue The default value if property not found
     * @return The property value or default value
     */
    public static long getLong(String key, long defaultValue) {
        Object value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid long value for key '" + key + "': " + value);
            return defaultValue;
        }
    }

    /**
     * Get a property value as Boolean
     *
     * @param key The property key in dot notation (e.g., "feature.enabled")
     * @param defaultValue The default value if property not found
     * @return The property value or default value
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        Object value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Get a property value as Double
     *
     * @param key The property key in dot notation (e.g., "rate.limit")
     * @param defaultValue The default value if property not found
     * @return The property value or default value
     */
    public static double getDouble(String key, double defaultValue) {
        Object value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid double value for key '" + key + "': " + value);
            return defaultValue;
        }
    }

    /**
     * Get a raw property value
     *
     * @param key The property key in dot notation (e.g., "server.port")
     * @return The property value or null if not found
     */
    @SuppressWarnings("unchecked")
    private static Object getProperty(String key) {
        if (properties == null || key == null || key.trim().isEmpty()) {
            return null;
        }

        String[] keys = key.split("\\.");
        Map<String, Object> current = properties;

        for (int i = 0; i < keys.length - 1; i++) {
            Object value = current.get(keys[i]);
            if (!(value instanceof Map)) {
                return null;
            }
            current = (Map<String, Object>) value;
        }

        Object value = current.get(keys[keys.length - 1]);

        // Handle placeholders like ${ENV_VAR:default_value} or ${ENV_VAR}
        if (value instanceof String) {
            String strValue = (String) value;
            value = resolvePlaceholders(strValue);
        }

        return value;
    }

    /**
     * Resolve placeholders in a string value
     * Supports: ${ENV_VAR:default_value} and ${ENV_VAR}
     *
     * @param value The value with placeholders
     * @return The resolved value
     */
    private static String resolvePlaceholders(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        String result = value;
        int startIndex;
        while ((startIndex = result.indexOf("${")) != -1) {
            int endIndex = result.indexOf("}", startIndex);
            if (endIndex == -1) {
                break;
            }

            String placeholder = result.substring(startIndex + 2, endIndex);
            String defaultValue = null;
            String varName = placeholder;

            // Check for default value syntax: VAR_NAME:default_value
            int colonIndex = placeholder.indexOf(':');
            if (colonIndex != -1) {
                varName = placeholder.substring(0, colonIndex);
                defaultValue = placeholder.substring(colonIndex + 1);
            }

            // Try environment variable first, then system property
            String resolvedValue = System.getenv(varName);
            if (resolvedValue == null) {
                resolvedValue = System.getProperty(varName);
            }
            if (resolvedValue == null) {
                resolvedValue = defaultValue;
            }

            // If still null, keep the original placeholder
            if (resolvedValue == null) {
                resolvedValue = "${" + placeholder + "}";
            }

            result = result.substring(0, startIndex) + resolvedValue + result.substring(endIndex + 1);
        }

        return result;
    }

    /**
     * Check if a property exists
     *
     * @param key The property key in dot notation
     * @return true if property exists, false otherwise
     */
    public static boolean hasProperty(String key) {
        return getProperty(key) != null;
    }

    /**
     * Reload properties from file (useful for testing or hot-reload)
     */
    public static void reload() {
        loadProperties();
    }

    /**
     * Get all properties as a map
     *
     * @return All properties
     */
    public static Map<String, Object> getAllProperties() {
        return properties;
    }
}
