package com.example.steambeta.backend.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(schema = "replica", name = "offering")
public class Offering {
    protected Offering() {}
    @Id
    @Column(name = "id", nullable = false, insertable = false, updatable = false)
    private UUID id;

    public UUID getId() { return id; }

    @Column(name = "university_id", nullable = false, insertable = false, updatable = false)
    private UUID universityId;

    public UUID getUniversityId() { return universityId; }

    @Column(name = "subject_id", nullable = false, insertable = false, updatable = false)
    private UUID subjectId;

    public UUID getSubjectId() { return subjectId; }

    @Column(name = "term_code", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String termCode;

    public String getTermCode() { return termCode; }

    @Column(name = "section_code", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String sectionCode;

    public String getSectionCode() { return sectionCode; }

    @Column(name = "enrollment_opened_at", nullable = true, insertable = false, updatable = false)
    private Instant enrollmentOpenedAt;

    public Instant getEnrollmentOpenedAt() { return enrollmentOpenedAt; }

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Instant getCreatedAt() { return createdAt; }
}
