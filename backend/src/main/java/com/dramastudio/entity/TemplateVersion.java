package com.dramastudio.entity;
import jakarta.persistence.*;
@Entity @Table(name="template_versions")
public class TemplateVersion {
  @Id public String id;
  @Column(name="template_id",nullable=false) public String templateId;
  @Column(nullable=false) public String version;
  @Column(nullable=false) public String status;
  @Column(name="schema_version",nullable=false) public String schemaVersion;
  @Column(name="definition_json",nullable=false) public String definitionJson;
  @Column(nullable=false) public String checksum;
  @Column(name="change_log") public String changeLog;
  @Column(name="created_by") public String createdBy;
  @Column(name="created_at",nullable=false) public String createdAt;
  @Column(name="published_at") public String publishedAt;
}
