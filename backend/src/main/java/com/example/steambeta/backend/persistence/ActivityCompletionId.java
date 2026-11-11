package com.example.steambeta.backend.persistence;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ActivityCompletionId implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "university_id", nullable = false)
    private UUID universityId;
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    @Column(name = "offering_id", nullable = false)
    private UUID offeringId;
    @Column(name = "activity_id", nullable = false)
    private UUID activityId;
    protected ActivityCompletionId() {}
    public ActivityCompletionId(UUID universityId, UUID studentId, UUID offeringId, UUID activityId) {
        this.universityId = Objects.requireNonNull(universityId);
        this.studentId = Objects.requireNonNull(studentId);
        this.offeringId = Objects.requireNonNull(offeringId);
        this.activityId = Objects.requireNonNull(activityId);
    }
    public UUID getUniversityId() { return universityId; }
    public UUID getStudentId() { return studentId; }
    public UUID getOfferingId() { return offeringId; }
    public UUID getActivityId() { return activityId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ActivityCompletionId that)) return false;
        return Objects.equals(universityId, that.universityId) && Objects.equals(studentId, that.studentId) && Objects.equals(offeringId, that.offeringId) && Objects.equals(activityId, that.activityId);
    }
    @Override public int hashCode() { return Objects.hash(universityId, studentId, offeringId, activityId); }
}
