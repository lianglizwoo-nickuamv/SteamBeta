package com.example.steambeta.backend.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read only. Call inside ActorTransactions with a server-validated actor. */
public interface StudentRepository extends Repository<Student, UUID> {
    @Query("select e from Student e where e.universityId = :universityId and e.id = :studentId")
    List<Student> read(@Param("universityId") UUID universityId, @Param("studentId") UUID studentId);
}
