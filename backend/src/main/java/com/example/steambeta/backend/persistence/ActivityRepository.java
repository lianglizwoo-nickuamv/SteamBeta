package com.example.steambeta.backend.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read only. Call inside ActorTransactions with a server-validated actor. */
public interface ActivityRepository extends Repository<Activity, UUID> {
    @Query("select e from Activity e where e.universityId = :universityId and e.offeringId = :offeringId")
    List<Activity> read(@Param("universityId") UUID universityId, @Param("offeringId") UUID offeringId);
}
