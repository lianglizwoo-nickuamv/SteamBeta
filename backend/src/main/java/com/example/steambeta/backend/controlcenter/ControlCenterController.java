package com.example.steambeta.backend.controlcenter;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/control-center")
public class ControlCenterController {
    private final ControlCenterService controlCenter;

    public ControlCenterController(ControlCenterService controlCenter) {
        this.controlCenter = controlCenter;
    }

    @GetMapping("/students")
    public List<ControlCenterService.StudentOverview> searchStudents(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "20") int limit) {
        return controlCenter.searchStudents(query, limit);
    }

    @PostMapping("/students/{targetAccountId}/sanctions")
    @ResponseStatus(HttpStatus.CREATED)
    public UUID applySanction(
            @PathVariable UUID targetAccountId,
            @RequestBody ControlCenterService.SanctionDraft draft) {
        return controlCenter.applySanction(targetAccountId, draft);
    }

    @GetMapping("/audit-logs")
    public List<ControlCenterService.AuditLogEntry> auditLogs(@RequestParam(defaultValue = "50") int limit) {
        return controlCenter.auditLogs(limit);
    }

    @PostMapping("/appeals/{sanctionId}/resolution")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolveAppeal(
            @PathVariable UUID sanctionId,
            @RequestBody ControlCenterService.AppealResolution resolution) {
        controlCenter.resolveAppeal(sanctionId, resolution);
    }
}
