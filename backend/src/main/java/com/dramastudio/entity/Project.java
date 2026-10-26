package com.dramastudio.entity;
import jakarta.persistence.*;
@Entity @Table(name="projects")
public class Project extends BaseEntity {
  @Column(name="owner_user_id", nullable=false) public String ownerUserId;
  @Column(nullable=false) public String name;
  public String description;
  @Column(nullable=false) public String status="ACTIVE";
  @Column(name="story_environment", nullable=false) public String storyEnvironment="";
  @Column(name="genres_json", nullable=false) public String genresJson="[]";
  @Column(name="drama_type", nullable=false) public String dramaType="动态漫";
  @Column(name="art_style", nullable=false) public String artStyle="半写实国漫";
  @Column(name="episode_count", nullable=false) public int episodeCount=10;
  @Column(name="episode_duration_sec", nullable=false) public int episodeDurationSec=90;
  @Column(name="cover_asset_id") public String coverAssetId;
  @Column(name="settings_json", nullable=false) public String settingsJson="{}";
  @Column(name="deleted_at") public String deletedAt;
}
