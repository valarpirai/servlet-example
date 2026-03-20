package com.example.servlet.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Represents a JavaScript module with metadata. */
@Getter
@Setter
public class Module {

  private String name;
  private String path;
  private String content;
  private Instant createdAt;
  private Instant updatedAt;
  private long size;

  public Module(String name, String path, String content) {
    this.name = name;
    this.path = path;
    this.content = content;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    this.size = content != null ? content.getBytes().length : 0;
  }

  /** Custom setter for content that updates timestamp and size. */
  public void setContent(String content) {
    this.content = content;
    this.updatedAt = Instant.now();
    this.size = content != null ? content.getBytes().length : 0;
  }
}
