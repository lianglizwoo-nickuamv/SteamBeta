package com.example.steambeta.backend.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(schema = "replica", name = "merit_definition")
public class MeritDefinition {
    protected MeritDefinition() {}
    @Id
    @Column(name = "id", nullable = false, insertable = false, updatable = false)
    private UUID id;

    public UUID getId() { return id; }

    @Column(name = "university_id", nullable = false, insertable = false, updatable = false)
    private UUID universityId;

    public UUID getUniversityId() { return universityId; }

    @Column(name = "code", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String code;

    public String getCode() { return code; }

    @Column(name = "kind", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private MeritKind kind;

    public MeritKind getKind() { return kind; }

    @Column(name = "scope", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private MeritScope scope;

    public MeritScope getScope() { return scope; }

    @Column(name = "title", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String title;

    public String getTitle() { return title; }

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Instant getCreatedAt() { return createdAt; }
}
