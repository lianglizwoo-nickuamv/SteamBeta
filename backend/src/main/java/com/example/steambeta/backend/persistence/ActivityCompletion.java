package com.example.steambeta.backend.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(schema = "replica", name = "activity_completion")
public class ActivityCompletion {
    protected ActivityCompletion() {}
    @EmbeddedId
    private ActivityCompletionId id;
    public ActivityCompletionId getId() { return id; }

    @Column(name = "completed_at", nullable = false, insertable = false, updatable = false)
    private Instant completedAt;

    public Instant getCompletedAt() { return completedAt; }
}
