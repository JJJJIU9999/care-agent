package com.jiu.careagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_item", schema = "app")
public class ServiceItem {
    @Id
    private UUID id;
    private String name;
    private String category;
    private String district;
    private String description;
    private BigDecimal price;
    private boolean enabled;
    @Column(name = "created_at")
    private Instant createdAt;

    protected ServiceItem() {
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDistrict() { return district; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public boolean isEnabled() { return enabled; }
}
