package com.dramastudio.entity;
import jakarta.persistence.*;
@Entity @Table(name="templates")
public class Template extends BaseEntity {
  @Column(name="owner_user_id") public String ownerUserId;
  @Column(name="node_type",nullable=false) public String nodeType;
  @Column(nullable=false) public String name;
  @Column(nullable=false) public String scope;
  @Column(nullable=false) public String status="ACTIVE";
  @Column(name="latest_version_no",nullable=false) public int latestVersionNo=1;
  @Column(name="deleted_at") public String deletedAt;
}
