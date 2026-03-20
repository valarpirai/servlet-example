package com.example.servlet.module;

import java.time.Instant;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
    this.updatedAt = Instant.now();
    this.size = content != null ? content.getBytes().length : 0;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }
}
