package com.example.steambeta.backend.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(schema = "replica", name = "store_item")
public class StoreItem {
    protected StoreItem() {}
    @Id
    @Column(name = "id", nullable = false, insertable = false, updatable = false)
    private UUID id;

    public UUID getId() { return id; }

    @Column(name = "university_id", nullable = false, insertable = false, updatable = false)
    private UUID universityId;

    public UUID getUniversityId() { return universityId; }

    @Column(name = "sku", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String sku;

    public String getSku() { return sku; }

    @Column(name = "name", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String name;

    public String getName() { return name; }

    @Column(name = "points_price", nullable = false, insertable = false, updatable = false)
    private long pointsPrice;

    public long getPointsPrice() { return pointsPrice; }

    @Column(name = "stock", nullable = false, insertable = false, updatable = false)
    private int stock;

    public int getStock() { return stock; }

    @Column(name = "active", nullable = false, insertable = false, updatable = false)
    private boolean active;

    public boolean getActive() { return active; }

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Instant getCreatedAt() { return createdAt; }
}
