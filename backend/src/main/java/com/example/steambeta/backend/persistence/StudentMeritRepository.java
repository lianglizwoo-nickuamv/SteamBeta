package com.example.steambeta.backend.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read only. Call inside ActorTransactions with a server-validated actor. */
public interface StudentMeritRepository extends Repository<StudentMerit, UUID> {
    @Query("select e from StudentMerit e where e.universityId = :universityId and e.studentId = :studentId order by e.awardedAt desc, e.id")
    List<StudentMerit> read(@Param("universityId") UUID universityId, @Param("studentId") UUID studentId);
}
