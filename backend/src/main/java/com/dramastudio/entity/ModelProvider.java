package com.dramastudio.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "model_providers")
public class ModelProvider extends BaseEntity {
  @Column(nullable = false) public String name;
  @Column(name = "base_url", nullable = false) public String baseUrl;
  @Column(name = "api_key_enc") public String apiKeyEnc;
  @Column(name = "is_default", nullable = false) public Integer isDefault = 0;
  @Column(nullable = false) public Integer enabled = 1;
  @Column(name = "sort_order", nullable = false) public Integer sortOrder = 0;
}
