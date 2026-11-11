package com.example.steambeta.backend.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(schema = "replica", name = "university")
public class University {
    protected University() {}
    @Id
    @Column(name = "id", nullable = false, insertable = false, updatable = false)
    private UUID id;

    public UUID getId() { return id; }

    @Column(name = "code", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String code;

    public String getCode() { return code; }

    @Column(name = "name", nullable = false, insertable = false, updatable = false, columnDefinition = "text")
    private String name;

    public String getName() { return name; }

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Instant getCreatedAt() { return createdAt; }
}
