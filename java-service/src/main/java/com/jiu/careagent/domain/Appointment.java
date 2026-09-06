package com.jiu.careagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment", schema = "app")
public class Appointment {
    @Id
    private UUID id;
    @Column(name = "draft_id")
    private UUID draftId;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "service_id")
    private UUID serviceId;
    @Column(name = "slot_id")
    private UUID slotId;
    @Column(name = "confirmed_price")
    private BigDecimal confirmedPrice;
    private String status;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Appointment() {
    }

    public Appointment(AppointmentDraft draft, BigDecimal price, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.draftId = draft.getId();
        this.userId = draft.getUserId();
        this.serviceId = draft.getServiceId();
        this.slotId = draft.getSlotId();
        this.confirmedPrice = price;
        this.status = "CONFIRMED";
        this.idempotencyKey = idempotencyKey;
        this.confirmedAt = Instant.now();
        this.createdAt = this.confirmedAt;
        this.updatedAt = this.confirmedAt;
    }

    public void cancel() {
        if (!"CANCELLED".equals(status)) {
            status = "CANCELLED";
            cancelledAt = Instant.now();
            updatedAt = cancelledAt;
        }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getSlotId() { return slotId; }
    public BigDecimal getConfirmedPrice() { return confirmedPrice; }
    public String getStatus() { return status; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
