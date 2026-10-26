package com.dramastudio.entity;
import jakarta.persistence.*;
@Entity @Table(name="assets")
public class Asset extends BaseEntity {
  @Column(name="project_id",nullable=false) public String projectId;
  @Column(name="logical_key",nullable=false) public String logicalKey;
  @Column(name="version_no",nullable=false) public int versionNo;
  @Column(name="parent_asset_id") public String parentAssetId;
  @Column(name="asset_type",nullable=false) public String assetType;
  @Column(nullable=false) public String name;
  @Column(name="content_status",nullable=false) public String contentStatus="draft";
  @Column(name="is_selected",nullable=false,columnDefinition="INTEGER") public boolean selected;
  @Column(name="is_locked",nullable=false,columnDefinition="INTEGER") public boolean locked;
  @Column(name="content_text") public String contentText;
  @Column(name="storage_key") public String storageKey;
  @Column(name="thumbnail_key") public String thumbnailKey;
  @Column(name="mime_type",nullable=false) public String mimeType="application/json";
  @Column(name="size_bytes") public Long sizeBytes;
  public String sha256;
  @Column(name="metadata_json",nullable=false) public String metadataJson="{}";
  @Column(name="node_run_id") public String nodeRunId;
  @Column(name="created_by",nullable=false) public String createdBy;
  @Column(name="deleted_at") public String deletedAt;
}
