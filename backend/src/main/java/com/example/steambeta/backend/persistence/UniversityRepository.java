package com.example.steambeta.backend.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read only. Call inside ActorTransactions with a server-validated actor. */
public interface UniversityRepository extends Repository<University, UUID> {
    @Query("select e from University e where e.id = :universityId")
    List<University> read(@Param("universityId") UUID universityId);
}
