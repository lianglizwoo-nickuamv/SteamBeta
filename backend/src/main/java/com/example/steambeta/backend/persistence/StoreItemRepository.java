package com.example.steambeta.backend.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read only. Call inside ActorTransactions with a server-validated actor. */
public interface StoreItemRepository extends Repository<StoreItem, UUID> {
    @Query("select e from StoreItem e where e.universityId = :universityId and e.active = true")
    List<StoreItem> read(@Param("universityId") UUID universityId);
}
