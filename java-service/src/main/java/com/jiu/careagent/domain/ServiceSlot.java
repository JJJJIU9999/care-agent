package com.jiu.careagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_slot", schema = "app")
public class ServiceSlot {
    @Id
    private UUID id;
    @Column(name = "service_id")
    private UUID serviceId;
    @Column(name = "start_at")
    private Instant startAt;
    @Column(name = "end_at")
    private Instant endAt;
    @Column(name = "total_capacity")
    private int totalCapacity;
    @Column(name = "remaining_capacity")
    private int remainingCapacity;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ServiceSlot() {
    }

    public UUID getId() { return id; }
    public UUID getServiceId() { return serviceId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public int getRemainingCapacity() { return remainingCapacity; }
}
