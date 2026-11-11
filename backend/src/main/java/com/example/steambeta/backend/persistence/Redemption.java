package com.example.steambeta.backend.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(schema = "replica", name = "redemption")
public class Redemption {
    protected Redemption() {}
    @Id
    @Column(name = "id", nullable = false, insertable = false, updatable = false)
    private UUID id;

    public UUID getId() { return id; }

    @Column(name = "university_id", nullable = false, insertable = false, updatable = false)
    private UUID universityId;

    public UUID getUniversityId() { return universityId; }

    @Column(name = "student_id", nullable = false, insertable = false, updatable = false)
    private UUID studentId;

    public UUID getStudentId() { return studentId; }

    @Column(name = "item_id", nullable = false, insertable = false, updatable = false)
    private UUID itemId;

    public UUID getItemId() { return itemId; }

    @Column(name = "idempotency_key", nullable = false, insertable = false, updatable = false)
    private UUID idempotencyKey;

    public UUID getIdempotencyKey() { return idempotencyKey; }

    @Column(name = "item_name", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String itemName;

    public String getItemName() { return itemName; }

    @Column(name = "points_charged", nullable = false, insertable = false, updatable = false)
    private long pointsCharged;

    public long getPointsCharged() { return pointsCharged; }

    @Column(name = "status", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private RedemptionStatus status;

    public RedemptionStatus getStatus() { return status; }

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Instant getCreatedAt() { return createdAt; }
}
