package com.dramastudio.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="users")
public class User extends BaseEntity {
  @Column(name="ai_connect_username", nullable=false, unique=true) public String aiConnectUsername;
  @Column(nullable=false) public String username;
  public String email;
  @Column(nullable=false) public String role="CREATOR";
  @Column(nullable=false) public String status="ACTIVE";
  @Column(name="quota_balance", nullable=false) public BigDecimal quotaBalance=BigDecimal.ZERO;
  @Column(name="last_login_at") public String lastLoginAt;
}
