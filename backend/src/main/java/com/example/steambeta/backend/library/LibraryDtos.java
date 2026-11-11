package com.example.steambeta.backend.library;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class LibraryDtos {
    private LibraryDtos() {}
    public record Identity(UUID studentId,String alias,String universityName) {}
    public record SubjectSummary(UUID offeringId,String code,String name,String termCode,String sectionCode,
                                 long requiredCount,long completedCount,BigDecimal progressPercent) {}
    public record Activity(UUID activityId,String code,String name,boolean required,boolean completed) {}
    public record Detail(SubjectSummary summary,List<Activity> activities) {}
    public record Csrf(String headerName,String token) {}
}
