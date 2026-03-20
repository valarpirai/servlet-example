package com.example.servlet.processor;

import com.example.servlet.model.Attachment;
import com.example.servlet.model.ProcessorResponse;
import com.example.servlet.storage.AttachmentManager;
import com.example.servlet.util.PropertiesUtil;
import com.example.servlet.util.ResponseHelper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUploadProcessor implements IRequestProcessor {

  private static final Logger logger = LoggerFactory.getLogger(FileUploadProcessor.class);
  private static final String CONTENT_TYPE = "multipart/form-data";
  private static final long MAX_FILE_SIZE =
      PropertiesUtil.getLong("upload.maxFileSize", 524288000L); // 500MB default
  private static final long MAX_REQUEST_SIZE =
      PropertiesUtil.getLong("upload.maxRequestSize", 1073741824L); // 1GB default
  private final AttachmentManager attachmentManager;

  public FileUploadProcessor() {
    this.attachmentManager = AttachmentManager.getInstance();
  }

  @Override
  public boolean supports(String contentType) {
    return contentType != null && contentType.toLowerCase().startsWith(CONTENT_TYPE);
  }

  @Override
  public ProcessorResponse process(HttpServletRequest request)
      throws IOException, ServletException {
    try {
      Collection<Part> parts = request.getParts();

      if (parts == null || parts.isEmpty()) {
        return ResponseHelper.badRequest("No multipart data provided");
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
            return ResponseHelper.errorResponse(
                413,
                "Payload Too Large",
                "File size exceeds maximum limit of " + (MAX_FILE_SIZE / 1024 / 1024) + " MB");
          }

          // Create attachment metadata
          Attachment attachment = new Attachment();
          attachment.setFileName(submittedFileName);
          attachment.setContentType(part.getContentType());

          // Store using chunked streaming (memory-efficient)
          // For 500MB file, only 1MB in memory at a time
          try (InputStream inputStream = part.getInputStream()) {
            attachment = attachmentManager.store(attachment, inputStream);
          }

          // Collect file info
          Map<String, Object> fileInfo = new HashMap<>();
          fileInfo.put("attachmentId", attachment.getId());
          fileInfo.put("fieldName", part.getName());
          fileInfo.put("fileName", submittedFileName);
          fileInfo.put("size", attachment.getSizeBytes());
          fileInfo.put("contentType", part.getContentType());
          fileInfo.put("storageType", attachment.getStorageType());
          fileInfo.put("hash", attachment.getHash());
          fileInfo.put("downloadUrl", "/api/attachment/" + attachment.getId() + "/download");

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

      return ResponseHelper.successResponse(responseData);

    } catch (IllegalStateException e) {
      // This can happen if the request is not multipart
      return ResponseHelper.badRequest(
          "Request is not a valid multipart request: " + e.getMessage());
    } catch (Exception e) {
      logger.error("Error processing file upload", e);
      return ResponseHelper.internalError("Error processing file upload: " + e.getMessage());
    }
  }

  @Override
  public String getContentType() {
    return CONTENT_TYPE;
  }
}
