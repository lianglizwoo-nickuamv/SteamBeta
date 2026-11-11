package com.example.steambeta.backend.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(schema = "replica", name = "enrollment")
public class Enrollment {
    protected Enrollment() {}
    @EmbeddedId
    private EnrollmentId id;
    public EnrollmentId getId() { return id; }

    @Column(name = "enrolled_at", nullable = false, insertable = false, updatable = false)
    private Instant enrolledAt;

    public Instant getEnrolledAt() { return enrolledAt; }
}
