package com.example.steambeta.backend.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read only. Call inside ActorTransactions with a server-validated actor. */
public interface ActivityCompletionRepository extends Repository<ActivityCompletion, ActivityCompletionId> {
    @Query("select e from ActivityCompletion e where e.id.universityId = :universityId and e.id.studentId = :studentId")
    List<ActivityCompletion> read(@Param("universityId") UUID universityId, @Param("studentId") UUID studentId);
}
