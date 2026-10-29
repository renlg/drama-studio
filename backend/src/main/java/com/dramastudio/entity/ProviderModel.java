package com.dramastudio.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "provider_models")
public class ProviderModel extends BaseEntity {
  @Column(name = "provider_id", nullable = false) public String providerId;
  /** TEXT | IMAGE | VIDEO | AUDIO */
  @Column(nullable = false) public String category;
  /** 上游真实模型名，如 agnes-video-2.5-flash */
  @Column(name = "model_id", nullable = false) public String modelId;
  @Column(name = "display_name", nullable = false) public String displayName;
  @Column(name = "is_default", nullable = false) public Integer isDefault = 0;
  @Column(nullable = false) public Integer enabled = 1;
  @Column(name = "sort_order", nullable = false) public Integer sortOrder = 0;
  @Column(name = "extra_json") public String extraJson;
}
