package com.dramastudio.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {
  @Id public String id;
  @Column(name="created_at", nullable=false) public String createdAt;
  @Column(name="updated_at", nullable=false) public String updatedAt;
  @PrePersist void create() { if (id == null) id = UUID.randomUUID().toString(); if (createdAt == null) createdAt = Instant.now().toString(); updatedAt = createdAt; }
  @PreUpdate void update() { updatedAt = Instant.now().toString(); }
}
