package com.example.steambeta.backend.persistence;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class PointCreditId implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "university_id", nullable = false)
    private UUID universityId;
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    @Column(name = "source_key", nullable = false, columnDefinition = "text")
    private String sourceKey;
    protected PointCreditId() {}
    public PointCreditId(UUID universityId, UUID studentId, String sourceKey) {
        this.universityId = Objects.requireNonNull(universityId);
        this.studentId = Objects.requireNonNull(studentId);
        this.sourceKey = Objects.requireNonNull(sourceKey);
    }
    public UUID getUniversityId() { return universityId; }
    public UUID getStudentId() { return studentId; }
    public String getSourceKey() { return sourceKey; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PointCreditId that)) return false;
        return Objects.equals(universityId, that.universityId) && Objects.equals(studentId, that.studentId) && Objects.equals(sourceKey, that.sourceKey);
    }
    @Override public int hashCode() { return Objects.hash(universityId, studentId, sourceKey); }
}
