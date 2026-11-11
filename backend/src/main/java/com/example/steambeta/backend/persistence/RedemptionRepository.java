package com.example.steambeta.backend.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read only. Call inside ActorTransactions with a server-validated actor. */
public interface RedemptionRepository extends Repository<Redemption, UUID> {
    @Query("select e from Redemption e where e.universityId = :universityId and e.studentId = :studentId order by e.createdAt desc, e.id")
    List<Redemption> read(@Param("universityId") UUID universityId, @Param("studentId") UUID studentId);
}
