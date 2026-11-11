package com.example.steambeta.backend.persistence;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class WalletId implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "university_id", nullable = false)
    private UUID universityId;
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    protected WalletId() {}
    public WalletId(UUID universityId, UUID studentId) {
        this.universityId = Objects.requireNonNull(universityId);
        this.studentId = Objects.requireNonNull(studentId);
    }
    public UUID getUniversityId() { return universityId; }
    public UUID getStudentId() { return studentId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WalletId that)) return false;
        return Objects.equals(universityId, that.universityId) && Objects.equals(studentId, that.studentId);
    }
    @Override public int hashCode() { return Objects.hash(universityId, studentId); }
}
