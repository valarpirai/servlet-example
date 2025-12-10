package com.example.servlet.util;

/**
 * Utility class to configure logging properties from application.yml
 * This must be called before any logger is initialized
 */
public class LoggingConfig {

    /**
     * Initialize logging configuration from application.yml
     * Sets system properties that logback.xml can reference
     */
    public static void initialize() {
        // Read logging configuration from application.yml
        String logLevel = PropertiesUtil.getString("logging.level", "INFO");
        String fileEnabled = String.valueOf(PropertiesUtil.getBoolean("logging.fileEnabled", true));
        String filePath = PropertiesUtil.getString("logging.filePath", "logs/application.log");
        String fileMaxSize = PropertiesUtil.getString("logging.fileMaxSize", "10MB");
        String fileMaxHistory = String.valueOf(PropertiesUtil.getInt("logging.fileMaxHistory", 30));
        String fileTotalSizeCap = PropertiesUtil.getString("logging.fileTotalSizeCap", "1GB");
        String consolePattern = PropertiesUtil.getString("logging.consolePattern",
            "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        String filePattern = PropertiesUtil.getString("logging.filePattern",
            "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");

        // Set system properties for logback to use (only if not already set by environment)
        setPropertyIfNotSet("LOG_LEVEL", logLevel);
        setPropertyIfNotSet("LOG_FILE_ENABLED", fileEnabled);
        setPropertyIfNotSet("LOG_FILE_PATH", filePath);
        setPropertyIfNotSet("LOG_FILE_MAX_SIZE", fileMaxSize);
        setPropertyIfNotSet("LOG_FILE_MAX_HISTORY", fileMaxHistory);
        setPropertyIfNotSet("LOG_FILE_TOTAL_SIZE_CAP", fileTotalSizeCap);
        setPropertyIfNotSet("LOG_CONSOLE_PATTERN", consolePattern);
        setPropertyIfNotSet("LOG_FILE_PATTERN", filePattern);
    }

    /**
     * Set a system property only if it's not already set
     * This allows environment variables to override application.yml settings
     */
    private static void setPropertyIfNotSet(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
