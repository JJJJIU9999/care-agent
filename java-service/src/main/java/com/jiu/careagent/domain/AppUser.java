package com.jiu.careagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user", schema = "app")
public class AppUser {
    @Id
    private UUID id;
    private String username;
    @Column(name = "password_hash")
    private String passwordHash;
    private String role;
    private boolean enabled;
    @Column(name = "created_at")
    private Instant createdAt;

    protected AppUser() {
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
}
