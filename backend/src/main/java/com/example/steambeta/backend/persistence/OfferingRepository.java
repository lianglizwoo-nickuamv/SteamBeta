package com.example.steambeta.backend.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read only. Call inside ActorTransactions with a server-validated actor. */
public interface OfferingRepository extends Repository<Offering, UUID> {
    @Query("select e from Offering e where e.universityId = :universityId")
    List<Offering> read(@Param("universityId") UUID universityId);
}
