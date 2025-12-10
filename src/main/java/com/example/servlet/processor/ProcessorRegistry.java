package com.example.servlet.processor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessorRegistry {

    private static final ProcessorRegistry INSTANCE = new ProcessorRegistry();

    private final Map<String, RequestProcessor> processors;

    private ProcessorRegistry() {
        this.processors = new ConcurrentHashMap<>();
    }

    public static ProcessorRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register a request processor
     *
     * @param processor The processor to register
     */
    public void register(RequestProcessor processor) {
        if (processor != null) {
            processors.put(processor.getContentType().toLowerCase(), processor);
        }
    }

    /**
     * Get a processor for the given content type
     *
     * @param contentType The content type to look up
     * @return The processor if found, null otherwise
     */
    public RequestProcessor getProcessor(String contentType) {
        if (contentType == null) {
            return null;
        }

        String normalizedContentType = contentType.toLowerCase();

        // Direct lookup
        RequestProcessor processor = processors.get(normalizedContentType);
        if (processor != null) {
            return processor;
        }

        // Check if any processor supports this content type (for variants like "application/json; charset=UTF-8")
        for (RequestProcessor p : processors.values()) {
            if (p.supports(contentType)) {
                return p;
            }
        }

        return null;
    }

    /**
     * Check if a processor exists for the given content type
     *
     * @param contentType The content type to check
     * @return true if a processor is registered for this content type
     */
    public boolean hasProcessor(String contentType) {
        return getProcessor(contentType) != null;
    }

    /**
     * Clear all registered processors (useful for testing)
     */
    public void clear() {
        processors.clear();
    }

    /**
     * Get the number of registered processors
     *
     * @return The count of registered processors
     */
    public int size() {
        return processors.size();
    }
}
