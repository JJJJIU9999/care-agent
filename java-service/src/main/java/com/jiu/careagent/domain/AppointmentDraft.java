package com.jiu.careagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_draft", schema = "app")
public class AppointmentDraft {
    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "service_id")
    private UUID serviceId;
    @Column(name = "slot_id")
    private UUID slotId;
    private String status;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected AppointmentDraft() {
    }

    public AppointmentDraft(UUID userId, UUID serviceId, UUID slotId, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.serviceId = serviceId;
        this.slotId = slotId;
        this.status = "PENDING";
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getSlotId() { return slotId; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
}
