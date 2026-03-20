package com.example.servlet.handler;

import com.example.servlet.storage.Attachment;
import com.example.servlet.storage.AttachmentManager;
import com.example.servlet.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler for attachment download operations. Streams files in chunks to avoid memory issues. */
public class AttachmentHandler {

  private static final Logger logger = LoggerFactory.getLogger(AttachmentHandler.class);
  private static AttachmentHandler instance;
  private final AttachmentManager attachmentManager;

  private AttachmentHandler() {
    this.attachmentManager = AttachmentManager.getInstance();
  }

  public static synchronized AttachmentHandler getInstance() {
    if (instance == null) {
      instance = new AttachmentHandler();
    }
    return instance;
  }

  /** Handle attachment download. Streams file in chunks - never loads entire file into memory. */
  public void handleDownload(
      HttpServletRequest request, HttpServletResponse response, String attachmentId)
      throws IOException {

    if (!attachmentManager.exists(attachmentId)) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      response.setContentType("application/json");
      response.getWriter().print(JsonUtil.errorResponse("Not Found", "Attachment not found", 404));
      return;
    }

    Attachment attachment = attachmentManager.getMetadata(attachmentId);

    if (attachment == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      response.setContentType("application/json");
      response
          .getWriter()
          .print(JsonUtil.errorResponse("Not Found", "Attachment metadata not found", 404));
      return;
    }

    // Set response headers
    response.setContentType(
        attachment.getContentType() != null
            ? attachment.getContentType()
            : "application/octet-stream");
    response.setHeader(
        "Content-Disposition", "attachment; filename=\"" + attachment.getFileName() + "\"");
    response.setContentLengthLong(attachment.getSizeBytes());

    // Stream file in chunks (memory-efficient)
    try (InputStream inputStream = attachmentManager.retrieve(attachmentId);
        OutputStream outputStream = response.getOutputStream()) {

      byte[] buffer = new byte[8192];
      int bytesRead;
      long totalBytes = 0;

      while ((bytesRead = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
        totalBytes += bytesRead;
      }

      logger.info(
          "Downloaded attachment {} ({} bytes streamed)", attachment.getFileName(), totalBytes);

    } catch (IOException e) {
      logger.error("Error streaming attachment {}", attachmentId, e);
      throw e;
    }
  }

  /** Handle attachment metadata retrieval. */
  public void handleMetadata(HttpServletResponse response, String attachmentId) throws IOException {

    Attachment attachment = attachmentManager.getMetadata(attachmentId);

    if (attachment == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      response.setContentType("application/json");
      response.getWriter().print(JsonUtil.errorResponse("Not Found", "Attachment not found", 404));
      return;
    }

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.getWriter().print(JsonUtil.successResponse(attachment));
  }

  /** Handle list all attachments. */
  public void handleList(HttpServletResponse response) throws IOException {
    java.util.List<Attachment> attachments = attachmentManager.listAll();

    java.util.Map<String, Object> data = new java.util.HashMap<>();
    data.put("attachments", attachments);
    data.put("count", attachments.size());

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.getWriter().print(JsonUtil.successResponse(data));
  }

  /** Handle attachment deletion. */
  public void handleDelete(HttpServletResponse response, String attachmentId) throws IOException {

    if (!attachmentManager.exists(attachmentId)) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      response.setContentType("application/json");
      response.getWriter().print(JsonUtil.errorResponse("Not Found", "Attachment not found", 404));
      return;
    }

    attachmentManager.delete(attachmentId);

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response
        .getWriter()
        .print(
            JsonUtil.successResponse(
                java.util.Map.of("message", "Attachment deleted", "attachmentId", attachmentId)));
  }
}
