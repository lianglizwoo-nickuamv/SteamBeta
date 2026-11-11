package com.example.steambeta.backend.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(schema = "replica", name = "activity")
public class Activity {
    protected Activity() {}
    @Id
    @Column(name = "id", nullable = false, insertable = false, updatable = false)
    private UUID id;

    public UUID getId() { return id; }

    @Column(name = "university_id", nullable = false, insertable = false, updatable = false)
    private UUID universityId;

    public UUID getUniversityId() { return universityId; }

    @Column(name = "offering_id", nullable = false, insertable = false, updatable = false)
    private UUID offeringId;

    public UUID getOfferingId() { return offeringId; }

    @Column(name = "code", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String code;

    public String getCode() { return code; }

    @Column(name = "name", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String name;

    public String getName() { return name; }

    @Column(name = "required", nullable = false, insertable = false, updatable = false)
    private boolean required;

    public boolean getRequired() { return required; }

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Instant getCreatedAt() { return createdAt; }
}
