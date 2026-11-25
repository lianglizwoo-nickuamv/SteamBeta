package com.example.steambeta.backend.report;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de reportes personales del estudiante autenticado.
 * No acepta ningún parámetro accountId: la identidad proviene exclusivamente de la sesión.
 */
@RestController
@RequestMapping("/api/me/reports")
public class PersonalReportController {

    private final PersonalReportService reports;

    public PersonalReportController(PersonalReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/academic-activity")
    public ResponseEntity<byte[]> academicActivity(
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return toResponse(reports.academicActivity(JasperReportEngine.Format.parse(format), from, to));
    }

    @GetMapping("/communities")
    public ResponseEntity<byte[]> communities(
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return toResponse(reports.myCommunities(JasperReportEngine.Format.parse(format), from, to));
    }

    @GetMapping("/academic-planning")
    public ResponseEntity<byte[]> academicPlanning(
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return toResponse(reports.academicPlanning(JasperReportEngine.Format.parse(format), from, to));
    }

    @GetMapping("/exchange-publications")
    public ResponseEntity<byte[]> exchangePublications(
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return toResponse(reports.exchangePublications(JasperReportEngine.Format.parse(format), from, to));
    }

    private static ResponseEntity<byte[]> toResponse(PersonalReportDtos.GeneratedReport report) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(report.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report.filename() + "\"")
                .cacheControl(CacheControl.noStore())
                .body(report.bytes());
    }
}
