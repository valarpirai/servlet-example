package com.example.servlet.processor;

import java.util.HashMap;
import java.util.Map;

public class ProcessorResponse {
    private final int statusCode;
    private final String contentType;
    private final String body;
    private final Map<String, String> headers;

    private ProcessorResponse(Builder builder) {
        this.statusCode = builder.statusCode;
        this.contentType = builder.contentType;
        this.body = builder.body;
        this.headers = builder.headers;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getContentType() {
        return contentType;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int statusCode = 200;
        private String contentType = "application/json";
        private String body = "";
        private Map<String, String> headers = new HashMap<>();

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public ProcessorResponse build() {
            return new ProcessorResponse(this);
        }
    }
}
