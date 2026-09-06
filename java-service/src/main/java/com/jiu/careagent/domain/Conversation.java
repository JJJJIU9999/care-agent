package com.jiu.careagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation", schema = "app")
public class Conversation {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    protected Conversation() {
    }

    public Conversation(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.createdAt = Instant.now();
        this.lastActiveAt = createdAt;
    }

    public void touch() {
        lastActiveAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
}
