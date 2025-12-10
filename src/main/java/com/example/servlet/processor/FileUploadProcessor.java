package com.example.servlet.processor;

import com.example.servlet.util.JsonUtil;
import com.example.servlet.util.PropertiesUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileUploadProcessor implements RequestProcessor {

    private static final String CONTENT_TYPE = "multipart/form-data";
    private static final long MAX_FILE_SIZE = PropertiesUtil.getLong("upload.maxFileSize", 10485760L);
    private static final long MAX_REQUEST_SIZE = PropertiesUtil.getLong("upload.maxRequestSize", 52428800L);

    @Override
    public boolean supports(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith(CONTENT_TYPE);
    }

    @Override
    public ProcessorResponse process(HttpServletRequest request) throws IOException, ServletException {
        try {
            Collection<Part> parts = request.getParts();

            if (parts == null || parts.isEmpty()) {
                return ProcessorResponse.builder()
                        .statusCode(400)
                        .body(JsonUtil.errorResponse("Bad Request", "No multipart data provided", 400))
                        .build();
            }

            List<Map<String, Object>> fileInfoList = new ArrayList<>();
            Map<String, String> formFields = new HashMap<>();

            for (Part part : parts) {
                String submittedFileName = part.getSubmittedFileName();

                if (submittedFileName != null && !submittedFileName.isEmpty()) {
                    // This is a file part
                    long size = part.getSize();

                    // Check file size
                    if (size > MAX_FILE_SIZE) {
                        return ProcessorResponse.builder()
                                .statusCode(413)
                                .body(JsonUtil.errorResponse(
                                        "Payload Too Large",
                                        "File size exceeds maximum limit of " + (MAX_FILE_SIZE / 1024 / 1024) + " MB",
                                        413))
                                .build();
                    }

                    // Save file to temp directory
                    Path tempFile = Files.createTempFile("upload-", "-" + submittedFileName);
                    try (InputStream inputStream = part.getInputStream()) {
                        Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    }

                    // Collect file info
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("fieldName", part.getName());
                    fileInfo.put("fileName", submittedFileName);
                    fileInfo.put("size", size);
                    fileInfo.put("contentType", part.getContentType());
                    fileInfo.put("savedPath", tempFile.toString());

                    fileInfoList.add(fileInfo);
                } else {
                    // This is a form field
                    String fieldValue = request.getParameter(part.getName());
                    if (fieldValue != null) {
                        formFields.put(part.getName(), fieldValue);
                    }
                }
            }

            // Create response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("files", fileInfoList);
            if (!formFields.isEmpty()) {
                responseData.put("fields", formFields);
            }
            responseData.put("fileCount", fileInfoList.size());

            String responseBody = JsonUtil.successResponse(responseData);

            return ProcessorResponse.builder()
                    .statusCode(200)
                    .body(responseBody)
                    .build();

        } catch (IllegalStateException e) {
            // This can happen if the request is not multipart
            return ProcessorResponse.builder()
                    .statusCode(400)
                    .body(JsonUtil.errorResponse(
                            "Bad Request",
                            "Request is not a valid multipart request: " + e.getMessage(),
                            400))
                    .build();
        } catch (Exception e) {
            return ProcessorResponse.builder()
                    .statusCode(500)
                    .body(JsonUtil.errorResponse(
                            "Internal Server Error",
                            "Error processing file upload: " + e.getMessage(),
                            500))
                    .build();
        }
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }
}
