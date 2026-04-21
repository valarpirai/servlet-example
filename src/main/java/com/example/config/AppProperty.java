package com.example.config;

import java.time.Instant;

/** Immutable representation of one row in the app_properties table. */
public class AppProperty {

  private final long id;
  private final String name;
  private final String value;
  private final String type;
  private final String description;
  private final boolean active;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final String createdBy;
  private final String updatedBy;

  public AppProperty(
      long id,
      String name,
      String value,
      String type,
      String description,
      boolean active,
      Instant createdAt,
      Instant updatedAt,
      String createdBy,
      String updatedBy) {
    this.id = id;
    this.name = name;
    this.value = value;
    this.type = type;
    this.description = description;
    this.active = active;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.createdBy = createdBy;
    this.updatedBy = updatedBy;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getValue() {
    return value;
  }

  public String getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }
}
