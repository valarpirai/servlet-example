package com.example.servlet.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateEngine {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");
    private static final Pattern FOR_LOOP_PATTERN = Pattern.compile("\\{\\{#for\\s+([a-zA-Z0-9_]+)\\s+in\\s+([a-zA-Z0-9_.]+)\\s*}}(.*)\\{\\{/for}}", Pattern.DOTALL);

    /**
     * Render a template with the given data
     *
     * @param templateContent The template content
     * @param data The data to render
     * @return The rendered HTML
     */
    public static String render(String templateContent, Map<String, Object> data) {
        if (templateContent == null || templateContent.isEmpty()) {
            return "";
        }

        String result = templateContent;

        // Process for loops first
        result = processForLoops(result, data);

        // Then process variables
        result = processVariables(result, data);

        return result;
    }

    /**
     * Load a template from classpath resources
     *
     * @param templatePath The path to the template file
     * @return The template content
     * @throws IOException if template cannot be loaded
     */
    public static String loadTemplate(String templatePath) throws IOException {
        try (InputStream is = TemplateEngine.class.getClassLoader().getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new IOException("Template not found: " + templatePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Process variable substitutions like {{variableName}}
     */
    private static String processVariables(String content, Map<String, Object> data) {
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = getNestedValue(variableName, data);
            String replacement = value != null ? escapeHtml(value.toString()) : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Process for loops like {{#for item in items}}...{{/for}}
     */
    private static String processForLoops(String content, Map<String, Object> data) {
        Matcher matcher = FOR_LOOP_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String itemName = matcher.group(1);
            String collectionName = matcher.group(2);
            String loopBody = matcher.group(3);

            Object collection = getNestedValue(collectionName, data);
            StringBuilder loopResult = new StringBuilder();

            if (collection instanceof Iterable) {
                for (Object item : (Iterable<?>) collection) {
                    // Create a new data context with the loop item
                    Map<String, Object> loopData = new java.util.HashMap<>(data);
                    loopData.put(itemName, item);
                    loopResult.append(processVariables(loopBody, loopData));
                }
            } else if (collection instanceof Object[]) {
                for (Object item : (Object[]) collection) {
                    Map<String, Object> loopData = new java.util.HashMap<>(data);
                    loopData.put(itemName, item);
                    loopResult.append(processVariables(loopBody, loopData));
                }
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(loopResult.toString()));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Get nested value using dot notation (e.g., "user.name")
     */
    @SuppressWarnings("unchecked")
    private static Object getNestedValue(String key, Map<String, Object> data) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        String[] parts = key.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * Escape HTML special characters to prevent XSS
     */
    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
