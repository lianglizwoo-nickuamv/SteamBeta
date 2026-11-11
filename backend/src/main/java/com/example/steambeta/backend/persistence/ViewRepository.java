package com.example.steambeta.backend.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Community reads still require an authenticated actor transaction. */
public interface ViewRepository extends Repository<Student, UUID> {
    @Query(value = """
        select university_id as "universityId", student_id as "studentId",
               offering_id as "offeringId", required_count as "requiredCount",
               completed_count as "completedCount", progress_percent as "progressPercent"
        from replica.student_progress
        where university_id = :universityId and student_id = :studentId
        order by offering_id
        """, nativeQuery = true)
    List<ReadViews.Progress> progress(@Param("universityId") UUID universityId,
                                     @Param("studentId") UUID studentId);

    @Query(value = """
        select profile_id as "profileId", alias, avatar_url as "avatarUrl",
               university_name as "universityName"
        from replica.community_profile where profile_id = :profileId
        """, nativeQuery = true)
    Optional<ReadViews.CommunityProfile> profile(@Param("profileId") UUID profileId);

    @Query(value = """
        select profile_id as "profileId", kind, title, awarded_at as "awardedAt"
        from replica.community_merit where profile_id = :profileId order by awarded_at desc
        """, nativeQuery = true)
    List<ReadViews.CommunityMerit> merits(@Param("profileId") UUID profileId);
}
