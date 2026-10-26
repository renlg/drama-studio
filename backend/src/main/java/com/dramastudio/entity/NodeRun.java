package com.dramastudio.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="node_runs")
public class NodeRun extends BaseEntity {
  @Column(name="workflow_run_id",nullable=false) public String workflowRunId;
  @Column(name="node_id",nullable=false) public String nodeId;
  @Column(name="node_type",nullable=false) public String nodeType;
  @Column(nullable=false) public String status="PENDING";
  @Column(nullable=false) public int attempt;
  @Column(nullable=false) public BigDecimal progress=BigDecimal.ZERO;
  @Column(name="input_asset_ids_json",nullable=false) public String inputAssetIdsJson="[]";
  @Column(name="input_fingerprint") public String inputFingerprint;
  @Column(name="config_snapshot_json",nullable=false) public String configSnapshotJson="{}";
  @Column(name="template_snapshot_json") public String templateSnapshotJson;
  @Column(name="resolved_variables_json") public String resolvedVariablesJson;
  @Column(name="rendered_prompt") public String renderedPrompt;
  @Column(name="rendered_negative_prompt") public String renderedNegativePrompt;
  public String provider; public String model;
  @Column(name="external_task_id") public String externalTaskId;
  @Column(name="idempotency_key",nullable=false,unique=true) public String idempotencyKey;
  @Column(name="error_code") public String errorCode;
  @Column(name="error_message") public String errorMessage;
  @Column(name="started_at") public String startedAt;
  @Column(name="finished_at") public String finishedAt;
  @Column(name="next_retry_at") public String nextRetryAt;
}
