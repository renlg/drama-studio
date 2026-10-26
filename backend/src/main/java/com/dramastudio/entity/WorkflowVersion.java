package com.dramastudio.entity;
import jakarta.persistence.*;
@Entity @Table(name="workflow_versions", uniqueConstraints=@UniqueConstraint(columnNames={"workflow_id","version_no"}))
public class WorkflowVersion {
  @Id public String id;
  @Column(name="workflow_id", nullable=false) public String workflowId;
  @Column(name="version_no", nullable=false) public int versionNo;
  @Column(name="snapshot_json", nullable=false) public String snapshotJson;
  @Column(nullable=false) public String checksum;
  @Column(name="published_by", nullable=false) public String publishedBy;
  @Column(name="created_at", nullable=false) public String createdAt;
}
