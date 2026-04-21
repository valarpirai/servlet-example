package com.example.config;

import java.time.Instant;

/** Immutable representation of one row in the app_properties table. */
public record AppProperty(
    long id,
    String name,
    String value,
    String type,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {}
