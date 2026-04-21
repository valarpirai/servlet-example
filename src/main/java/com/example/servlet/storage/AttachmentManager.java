package com.example.servlet.storage;

import com.example.config.Database;
import com.example.config.DbPropertiesLoader;
import com.example.servlet.model.Attachment;
import com.example.servlet.util.PropertiesUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central manager for attachment storage. Delegates to configured storage strategy (filesystem, s3,
 * database).
 */
public class AttachmentManager {

  private static final Logger logger = LoggerFactory.getLogger(AttachmentManager.class);
  private static AttachmentManager instance;

  private final AttachmentStorage storage;
  private final Map<String, Attachment> metadataCache;

  private AttachmentManager() {
    String storageType = PropertiesUtil.getString("storage.type", "filesystem");
    this.storage = createStorage(storageType);
    this.metadataCache = new ConcurrentHashMap<>();

    // Load all metadata on startup
    loadAllMetadata();

    logger.info("AttachmentManager initialized with storage: {}", storageType);
  }

  private void loadAllMetadata() {
    try {
      java.util.List<Attachment> attachments = storage.listAll();
      for (Attachment attachment : attachments) {
        metadataCache.put(attachment.getId(), attachment);
      }
      logger.info("Loaded {} attachment metadata entries", attachments.size());
    } catch (IOException e) {
      logger.error("Failed to load attachment metadata", e);
    }
  }

  public static synchronized AttachmentManager getInstance() {
    if (instance == null) {
      instance = new AttachmentManager();
    }
    return instance;
  }

  /**
   * Store attachment using chunked streaming. Memory-efficient: processes file in 1MB chunks.
   *
   * @param attachment Metadata
   * @param inputStream File data
   * @return Stored attachment with ID
   * @throws IOException if storage fails
   */
  public Attachment store(Attachment attachment, InputStream inputStream) throws IOException {
    Attachment stored = storage.store(attachment, inputStream);
    metadataCache.put(stored.getId(), stored);

    logger.info(
        "Stored attachment: {} ({} bytes, {})",
        stored.getFileName(),
        stored.getSizeBytes(),
        stored.getStorageType());

    return stored;
  }

  /**
   * Retrieve attachment as stream. Memory-efficient: streams chunks, never loads full file.
   *
   * @param attachmentId Attachment ID
   * @return InputStream that streams data
   * @throws IOException if retrieval fails
   */
  public InputStream retrieve(String attachmentId) throws IOException {
    return storage.retrieve(attachmentId);
  }

  /**
   * Get attachment metadata.
   *
   * @param attachmentId Attachment ID
   * @return Attachment metadata or null if not found
   */
  public Attachment getMetadata(String attachmentId) {
    // Check cache first
    Attachment cached = metadataCache.get(attachmentId);
    if (cached != null) {
      return cached;
    }

    // Load from storage if not in cache
    try {
      Attachment loaded = storage.loadMetadata(attachmentId);
      if (loaded != null) {
        metadataCache.put(attachmentId, loaded);
      }
      return loaded;
    } catch (IOException e) {
      logger.error("Failed to load metadata for {}", attachmentId, e);
      return null;
    }
  }

  /**
   * List all attachments.
   *
   * @return List of all attachments
   */
  public java.util.List<Attachment> listAll() {
    return new java.util.ArrayList<>(metadataCache.values());
  }

  /**
   * Delete attachment.
   *
   * @param attachmentId Attachment ID
   * @throws IOException if deletion fails
   */
  public void delete(String attachmentId) throws IOException {
    storage.delete(attachmentId);
    metadataCache.remove(attachmentId);

    logger.info("Deleted attachment: {}", attachmentId);
  }

  /**
   * Check if attachment exists.
   *
   * @param attachmentId Attachment ID
   * @return true if exists
   */
  public boolean exists(String attachmentId) {
    return storage.exists(attachmentId);
  }

  private AttachmentStorage createStorage(String storageType) {
    switch (storageType.toLowerCase()) {
      case "filesystem":
        return new LocalFileSystemStorage();
      case "s3":
        throw new UnsupportedOperationException("S3 storage not yet implemented");
      case "database":
        Database db = DbPropertiesLoader.getDatabase();
        if (db == null) {
          throw new IllegalStateException(
              "storage.type=database but DB connection is not initialised");
        }
        int chunkSize = PropertiesUtil.getInt("storage.chunkSize", 1048576);
        return new DatabaseAttachmentStorage(db, chunkSize);
      default:
        logger.warn("Unknown storage type: {}, falling back to filesystem", storageType);
        return new LocalFileSystemStorage();
    }
  }
}
