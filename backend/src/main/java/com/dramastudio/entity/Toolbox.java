package com.dramastudio.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="toolboxes")
public class Toolbox {
  @Id public String id;
  @Column(name="user_id",nullable=false) public String userId;
  @Column(nullable=false) public String name;
  @Column(name="nodes_json",nullable=false,columnDefinition="TEXT") public String nodesJson;
  @Column(name="edges_json",nullable=false,columnDefinition="TEXT") public String edgesJson;
  @Column(name="created_at",nullable=false) public String createdAt;
  @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();if(createdAt==null)createdAt=Instant.now().toString();}
}
