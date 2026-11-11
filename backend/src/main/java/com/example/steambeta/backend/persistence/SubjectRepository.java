package com.example.steambeta.backend.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read only. Call inside ActorTransactions with a server-validated actor. */
public interface SubjectRepository extends Repository<Subject, UUID> {
    @Query("select e from Subject e where e.universityId = :universityId")
    List<Subject> read(@Param("universityId") UUID universityId);
}
