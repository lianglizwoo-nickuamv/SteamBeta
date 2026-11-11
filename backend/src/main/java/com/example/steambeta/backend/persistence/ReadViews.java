package com.example.steambeta.backend.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ReadViews {
    private ReadViews() {}
    public interface Progress {
        UUID getUniversityId();
        UUID getStudentId();
        UUID getOfferingId();
        long getRequiredCount();
        long getCompletedCount();
        BigDecimal getProgressPercent();
    }
    public interface CommunityProfile {
        UUID getProfileId();
        String getAlias();
        String getAvatarUrl();
        String getUniversityName();
    }
    public interface CommunityMerit {
        UUID getProfileId();
        String getKind();
        String getTitle();
        Instant getAwardedAt();
    }
}
