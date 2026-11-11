package com.example.steambeta.backend.library;

import com.example.steambeta.backend.persistence.Offering;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LibraryRepository extends Repository<Offering,UUID> {
    interface SummaryRow {
        UUID getOfferingId(); String getCode(); String getName(); String getTermCode(); String getSectionCode();
        long getRequiredCount(); long getCompletedCount(); BigDecimal getProgressPercent();
    }
    interface ActivityRow { UUID getActivityId(); String getCode(); String getName(); boolean getRequired(); boolean getCompleted(); }
    interface IdentityRow { UUID getStudentId(); String getAlias(); String getUniversityName(); }
    String SUMMARY="""
        select o.id as "offeringId",s.code,s.name,o.term_code as "termCode",o.section_code as "sectionCode",
               p.required_count as "requiredCount",p.completed_count as "completedCount",p.progress_percent as "progressPercent"
        from replica.student_progress p
        join replica.offering o on o.university_id=p.university_id and o.id=p.offering_id
        join replica.subject s on s.university_id=o.university_id and s.id=o.subject_id
        where p.university_id=replica.current_university_id() and p.student_id=replica.current_student_id()
        """;
    @Query(value=SUMMARY+" order by s.name,o.term_code,o.section_code,o.id",nativeQuery=true)
    List<SummaryRow> library();
    @Query(value=SUMMARY+" and o.id=:offeringId",nativeQuery=true)
    Optional<SummaryRow> summary(@Param("offeringId") UUID offeringId);
    @Query(value="""
        select a.id as "activityId",a.code,a.name,a.required,(c.activity_id is not null) as completed
        from replica.enrollment e
        join replica.activity a on a.university_id=e.university_id and a.offering_id=e.offering_id
        left join replica.activity_completion c on c.university_id=e.university_id and c.student_id=e.student_id
            and c.offering_id=e.offering_id and c.activity_id=a.id
        where e.university_id=replica.current_university_id() and e.student_id=replica.current_student_id()
            and e.offering_id=:offeringId
        order by a.required desc,a.code,a.id
        """,nativeQuery=true)
    List<ActivityRow> activities(@Param("offeringId") UUID offeringId);
    @Query(value="""
        select s.id as "studentId",s.alias,u.name as "universityName"
        from replica.student s join replica.university u on u.id=s.university_id
        where s.university_id=replica.current_university_id() and s.id=replica.current_student_id()
        """,nativeQuery=true)
    Optional<IdentityRow> identity();
}
