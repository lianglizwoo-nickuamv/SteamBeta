package com.example.steambeta.backend.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(schema = "replica", name = "student_merit")
public class StudentMerit {
    protected StudentMerit() {}
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

    @Column(name = "merit_id", nullable = false, insertable = false, updatable = false)
    private UUID meritId;

    public UUID getMeritId() { return meritId; }

    @Column(name = "scope", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private MeritScope scope;

    public MeritScope getScope() { return scope; }

    @Column(name = "offering_id", nullable = true, insertable = false, updatable = false)
    private UUID offeringId;

    public UUID getOfferingId() { return offeringId; }

    @Column(name = "source_key", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String sourceKey;

    public String getSourceKey() { return sourceKey; }

    @Column(name = "awarded_at", nullable = false, insertable = false, updatable = false)
    private Instant awardedAt;

    public Instant getAwardedAt() { return awardedAt; }
}
