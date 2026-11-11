package com.example.steambeta.backend.persistence;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EnrollmentId implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "university_id", nullable = false)
    private UUID universityId;
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    @Column(name = "offering_id", nullable = false)
    private UUID offeringId;
    protected EnrollmentId() {}
    public EnrollmentId(UUID universityId, UUID studentId, UUID offeringId) {
        this.universityId = Objects.requireNonNull(universityId);
        this.studentId = Objects.requireNonNull(studentId);
        this.offeringId = Objects.requireNonNull(offeringId);
    }
    public UUID getUniversityId() { return universityId; }
    public UUID getStudentId() { return studentId; }
    public UUID getOfferingId() { return offeringId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EnrollmentId that)) return false;
        return Objects.equals(universityId, that.universityId) && Objects.equals(studentId, that.studentId) && Objects.equals(offeringId, that.offeringId);
    }
    @Override public int hashCode() { return Objects.hash(universityId, studentId, offeringId); }
}
