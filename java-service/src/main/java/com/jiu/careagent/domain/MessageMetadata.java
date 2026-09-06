package com.jiu.careagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_metadata", schema = "app")
public class MessageMetadata {
    @Id
    private UUID id;
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;
    @Column(nullable = false)
    private String role;
    @Column(name = "char_count", nullable = false)
    private int charCount;
    @Column(name = "token_count")
    private Integer tokenCount;
    private String model;
    @Column(name = "duration_ms")
    private Long durationMs;
    @Column(nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MessageMetadata() {
    }

    public MessageMetadata(UUID conversationId, String role, int charCount, Long durationMs, String status) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.role = role;
        this.charCount = charCount;
        this.durationMs = durationMs;
        this.status = status;
        this.createdAt = Instant.now();
    }
}
