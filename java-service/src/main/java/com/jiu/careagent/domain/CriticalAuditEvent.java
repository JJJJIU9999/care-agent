package com.jiu.careagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "critical_audit_event", schema = "app")
public class CriticalAuditEvent {
    @Id
    private UUID id;
    @Column(name = "event_type")
    private String eventType;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "request_id")
    private UUID requestId;
    private String result;
    @Column(name = "target_resource_id")
    private UUID targetResourceId;
    @Column(name = "created_at")
    private Instant createdAt;

    protected CriticalAuditEvent() {
    }

    public CriticalAuditEvent(String eventType, UUID userId, UUID requestId, String result, UUID targetResourceId) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.userId = userId;
        this.requestId = requestId;
        this.result = result;
        this.targetResourceId = targetResourceId;
        this.createdAt = Instant.now();
    }
}
