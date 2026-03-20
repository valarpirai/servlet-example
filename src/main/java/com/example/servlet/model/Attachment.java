package com.example.servlet.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Represents an uploaded file attachment with metadata. */
@Getter
@Setter
public class Attachment {

  private String id;
  private String fileName;
  private String contentType;
  private long sizeBytes;
  private String hash; // SHA-256 for deduplication
  private String storageType; // filesystem, s3, database
  private String storagePath;
  private Instant createdAt;
  private Instant updatedAt;

  /** Constructor that initializes timestamps. */
  public Attachment() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }
}
