package com.example.servlet.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Strategy interface for attachment storage.
 * Implementations: LocalFileSystemStorage, S3Storage, DatabaseStorage
 */
public interface AttachmentStorage {

  /**
   * Store attachment using chunked streaming.
   * Never loads entire file into memory.
   *
   * @param attachment Metadata for the attachment
   * @param inputStream Source data stream
   * @return Updated attachment with storage path
   * @throws IOException if storage fails
   */
  Attachment store(Attachment attachment, InputStream inputStream) throws IOException;

  /**
   * Retrieve attachment as stream.
   * Returns chunks one at a time to avoid memory issues.
   *
   * @param attachmentId Attachment identifier
   * @return InputStream that streams chunks
   * @throws IOException if retrieval fails
   */
  InputStream retrieve(String attachmentId) throws IOException;

  /**
   * Delete attachment and all its chunks.
   *
   * @param attachmentId Attachment identifier
   * @throws IOException if deletion fails
   */
  void delete(String attachmentId) throws IOException;

  /**
   * Check if attachment exists.
   *
   * @param attachmentId Attachment identifier
   * @return true if exists
   */
  boolean exists(String attachmentId);

  /**
   * Get storage type identifier.
   *
   * @return Storage type (filesystem, s3, database)
   */
  String getStorageType();

  /**
   * List all attachments.
   *
   * @return List of all attachment metadata
   * @throws IOException if listing fails
   */
  java.util.List<Attachment> listAll() throws IOException;

  /**
   * Load attachment metadata.
   *
   * @param attachmentId Attachment identifier
   * @return Attachment metadata or null if not found
   * @throws IOException if loading fails
   */
  Attachment loadMetadata(String attachmentId) throws IOException;
}
