package com.example.steambeta.backend.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(schema = "replica", name = "point_credit")
public class PointCredit {
    protected PointCredit() {}
    @EmbeddedId
    private PointCreditId id;
    public PointCreditId getId() { return id; }

    @Column(name = "points", nullable = false, insertable = false, updatable = false)
    private long points;

    public long getPoints() { return points; }

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Instant getCreatedAt() { return createdAt; }
}
