package com.dramastudio.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="workflow_runs")
public class WorkflowRun extends BaseEntity {
  @Column(name="workflow_id",nullable=false) public String workflowId;
  @Column(name="workflow_version_id",nullable=false) public String workflowVersionId;
  @Column(name="project_id",nullable=false) public String projectId;
  @Column(name="trigger_type",nullable=false) public String triggerType="MANUAL";
  @Column(name="target_node_ids_json") public String targetNodeIdsJson;
  @Column(nullable=false) public String status="PENDING";
  @Column(nullable=false) public BigDecimal progress=BigDecimal.ZERO;
  @Column(name="input_snapshot_json",nullable=false) public String inputSnapshotJson="{}";
  @Column(name="estimated_cost",nullable=false) public BigDecimal estimatedCost=BigDecimal.ZERO;
  @Column(name="actual_cost",nullable=false) public BigDecimal actualCost=BigDecimal.ZERO;
  @Column(name="started_at") public String startedAt;
  @Column(name="finished_at") public String finishedAt;
  @Column(name="created_by",nullable=false) public String createdBy;
}
