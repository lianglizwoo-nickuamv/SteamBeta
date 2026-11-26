package com.example.steambeta.backend.admin;

import com.example.steambeta.backend.controlcenter.ControlCenterService.AuditLogEntry;
import com.example.steambeta.backend.report.JasperReportEngine;
import com.example.steambeta.backend.report.PersonalReportDtos.GeneratedReport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API REST exclusiva para STEAMBETA ADMIN (/api/admin/**).
 * Protegida por ROLE_ADMIN_CONSOLE (completamente separada de las sesiones de estudiantes).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminConsoleController {

    public record AdminKeyLoginRequest(String adminKey) {}
    public record AdminSessionView(boolean authenticated, Instant authenticatedAt, AdminConsoleDataService.AdminCapabilities capabilities) {}
    public record DeactivateRequest(String reason) {}
    public record DeleteStudentRequest(String confirmationText, String reason) {}

    private final AdminConsoleAuthService auth;
    private final AdminConsoleDataService data;
    private final AdminReportService reports;
    private final AdminStudentReportService studentReports;
    private final AdminStudentRemovalService studentRemoval;

    public AdminConsoleController(AdminConsoleAuthService auth, AdminConsoleDataService data,
                                  AdminReportService reports, AdminStudentReportService studentReports,
                                  AdminStudentRemovalService studentRemoval) {
        this.auth = auth;
        this.data = data;
        this.reports = reports;
        this.studentReports = studentReports;
        this.studentRemoval = studentRemoval;
    }

    @PostMapping("/auth/login")
    public AdminSessionView login(@RequestBody(required = false) AdminKeyLoginRequest req,
                                  HttpServletRequest request, HttpServletResponse response) {
        String key = req != null ? req.adminKey() : null;
        AdminConsolePrincipal principal = auth.authenticate(key, request, response);
        return new AdminSessionView(true, principal.authenticatedAt(), data.capabilities());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        auth.logout(request);
    }

    @GetMapping("/session")
    public AdminSessionView session() {
        AdminConsolePrincipal principal = auth.requireAdminSession();
        return new AdminSessionView(true, principal.authenticatedAt(), data.capabilities());
    }

    @GetMapping("/dashboard")
    public AdminConsoleDataService.AdminDashboard dashboard() {
        auth.requireAdminSession();
        return data.dashboard();
    }

    @GetMapping("/users")
    public AdminConsoleDataService.PagedUsers users(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "ALL") String role,
            @RequestParam(defaultValue = "CREATED_DESC") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        auth.requireAdminSession();
        return data.directory(query, status, role, sortBy, page, size);
    }

    @GetMapping("/users/{accountId}")
    public AdminConsoleDataService.StudentDossier dossier(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String query) {
        auth.requireAdminSession();
        return data.dossier(accountId, query);
    }

    @PostMapping("/users/{accountId}/deactivate")
    public UUID deactivate(@PathVariable UUID accountId, @RequestBody(required = false) DeactivateRequest req) {
        auth.requireAdminSession();
        return data.deactivateAccount(accountId, req != null ? req.reason() : null);
    }

    @GetMapping("/users/{accountId}/removal-preview")
    public AdminStudentRemovalService.StudentRemovalPreview removalPreview(@PathVariable UUID accountId) {
        auth.requireAdminSession();
        return studentRemoval.preview(accountId);
    }

    @PostMapping("/users/{accountId}/delete")
    public AdminStudentRemovalService.StudentDeletionResult deleteStudent(
            @PathVariable UUID accountId,
            @RequestBody(required = false) DeleteStudentRequest req) {
        auth.requireAdminSession();
        return studentRemoval.deletePhysically(
                accountId,
                req != null ? req.confirmationText() : null,
                req != null ? req.reason() : null);
    }

    @GetMapping("/audit-logs")
    public List<AuditLogEntry> auditLogs(@RequestParam(defaultValue = "50") int limit) {
        auth.requireAdminSession();
        return data.auditLogs(limit);
    }

    @GetMapping("/reports/users-full")
    public ResponseEntity<byte[]> fullUsersReport(
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) String query) {
        auth.requireAdminSession();
        return toResponse(reports.generateFullUsersReport(JasperReportEngine.Format.parse(format), query));
    }

    @GetMapping("/reports/{kind}")
    public ResponseEntity<byte[]> institutionalReport(
            @PathVariable String kind,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        auth.requireAdminSession();
        return toResponse(reports.generateInstitutional(kind, JasperReportEngine.Format.parse(format), query, from, to));
    }

    @GetMapping("/reports/dossier/{accountId}")
    public ResponseEntity<byte[]> studentDossierReport(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) String query) {
        auth.requireAdminSession();
        return toResponse(reports.generateDossier(accountId, JasperReportEngine.Format.parse(format), query));
    }

    @GetMapping("/users/{accountId}/reports/{reportKind}")
    public ResponseEntity<byte[]> studentPersonalReport(
            @PathVariable UUID accountId,
            @PathVariable String reportKind,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String query) {
        auth.requireAdminSession();
        return toResponse(studentReports.generateForStudent(
                accountId, reportKind, JasperReportEngine.Format.parse(format), from, to, query));
    }

    private static ResponseEntity<byte[]> toResponse(GeneratedReport report) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(report.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report.filename() + "\"")
                .cacheControl(CacheControl.noStore())
                .body(report.bytes());
    }
}
