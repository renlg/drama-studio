package com.dramastudio.entity;
import jakarta.persistence.*;
@Entity @Table(name="workflows")
public class Workflow extends BaseEntity {
  @Column(name="project_id", nullable=false) public String projectId;
  @Column(nullable=false) public String name;
  public String description;
  @Column(nullable=false) public String status="DRAFT";
  @Column(name="canvas_json", nullable=false) public String canvasJson="{}";
  @Version @Column(nullable=false) public long revision;
  @Column(name="latest_version_no", nullable=false) public int latestVersionNo;
  @Column(name="created_by", nullable=false) public String createdBy;
  @Column(name="deleted_at") public String deletedAt;
}
