package com.example.steambeta.backend.admin;

import static org.junit.jupiter.api.Assertions.*;

import com.example.steambeta.backend.report.JasperReportEngine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class AdminConsoleTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unconfiguredAdminKeyAlwaysRejects() {
        AdminConsoleAuthService svc = new AdminConsoleAuthService("", "");
        assertFalse(svc.isConfigured());
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        var res = new MockHttpServletResponse();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.authenticate("any-key", req, res));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void sha256ConstantTimeAuthAndRateLimitWork() throws Exception {
        String secret = "SteamBeta-Test-Admin-Secret-2026!";
        String sha256Hex = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));

        AdminConsoleAuthService svc = new AdminConsoleAuthService(sha256Hex, "");
        assertTrue(svc.isConfigured());

        var req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.2");
        var res = new MockHttpServletResponse();

        // 5 failed attempts -> 401
        for (int i = 0; i < 5; i++) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> svc.authenticate("wrong-key", req, res));
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }

        // 6th attempt from same IP -> 429 even with correct key
        ResponseStatusException rateEx = assertThrows(ResponseStatusException.class,
                () -> svc.authenticate(secret, req, res));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rateEx.getStatusCode());

        // Different IP with valid key succeeds and establishes ROLE_ADMIN_CONSOLE
        var okReq = new MockHttpServletRequest();
        okReq.setRemoteAddr("10.0.0.3");
        var oldSession = okReq.getSession(true);
        assertNotNull(oldSession);

        AdminConsolePrincipal principal = svc.authenticate(secret, okReq, res);
        assertNotNull(principal);
        assertNotNull(principal.authenticatedAt());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN_CONSOLE".equals(a.getAuthority())));
        assertNotSame(oldSession, okReq.getSession(false));

        // Logout clears context and invalidates session
        svc.logout(okReq);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(okReq.getSession(false));
    }

    @Test
    void institutionalAdminReportsCompileAndExportPdfAndXlsx() {
        JasperReportEngine engine = new JasperReportEngine();

        Map<String, Object> dirParams = new HashMap<>();
        dirParams.put("reportTitle", "Directorio general de usuarios");
        dirParams.put("reportType", "DIRECTORY");
        dirParams.put("filtersApplied", "Sin filtros (ventana V11 completa)");
        dirParams.put("generatedAt", "25/11/2026 11:00");
        dirParams.put("summaryLine", "Total en ventana V11: 1 · Activos: 1 · Desactivados: 0");
        dirParams.put("emptyNotice", "Datos extraídos mediante V11.");

        List<AdminReportService.AdminUserRow> rows = List.of(
                new AdminReportService.AdminUserRow(
                        "USUARIOS ESTUDIANTILES AUTORIZADOS",
                        "Estudiante Demo\nLogin: demo.student\nRol: USER\nUUID: 00000000-0000-0000-0000-000000000001",
                        "demo@example.edu\nVerificado",
                        "Univ.: UAM\nCarrera: Ingeniería en Sistemas\nSemestre: 4 · Avatar: Sin imagen",
                        "Bio: Estudiante de ingeniería\nIntereses: IA, Java",
                        "Comunidades: 2\nPublicaciones: 5\nTareas: 3\nIntercambios: 1",
                        "25/11/2026"
                )
        );

        byte[] dirPdf = engine.render("admin/admin_directory_report", dirParams, rows, JasperReportEngine.Format.PDF, "Admin");
        byte[] dirXlsx = engine.render("admin/admin_directory_report", dirParams, rows, JasperReportEngine.Format.XLSX, "Admin");
        assertTrue(dirPdf.length > 500 && new String(dirPdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"));
        assertTrue(dirXlsx.length > 500 && dirXlsx[0] == 'P' && dirXlsx[1] == 'K');

        Map<String, Object> dosParams = new HashMap<>();
        dosParams.put("generatedAt", "25/11/2026 11:00");
        dosParams.put("alias", "Estudiante Demo");
        dosParams.put("loginIdentifier", "demo.student");
        dosParams.put("emailNotice", "No disponible en funciones V11 actuales");
        dosParams.put("createdAtLabel", "25/11/2026 10:00");
        dosParams.put("statusLabel", "Activa");
        dosParams.put("roleLabel", "USER");
        dosParams.put("university", "UAM");
        dosParams.put("career", "Ingeniería en Sistemas");
        dosParams.put("semesterNotice", "No disponible en funciones V11 actuales");
        dosParams.put("bioNotice", "No disponible en funciones V11 actuales");
        dosParams.put("interestsNotice", "No disponible en funciones V11 actuales");
        dosParams.put("communitiesCount", "2");
        dosParams.put("postsCount", "5");
        dosParams.put("activeSanctionsCount", "0");
        dosParams.put("extendedActivityNotice", "No disponible sin V18");
        dosParams.put("moderationDetailNotice", "Sanciones activas: 0");

        byte[] dosPdf = engine.render("admin/admin_student_dossier", dosParams, List.of(), JasperReportEngine.Format.PDF, "Expediente");
        byte[] dosXlsx = engine.render("admin/admin_student_dossier", dosParams, List.of(), JasperReportEngine.Format.XLSX, "Expediente");
        assertTrue(dosPdf.length > 500 && new String(dosPdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"));
        assertTrue(dosXlsx.length > 500 && dosXlsx[0] == 'P' && dosXlsx[1] == 'K');
    }

    @Test
    void studentLoginRejectsTechnicalAdminIdentity() {
        var lookup = new com.example.steambeta.backend.auth.AccountLookup(null, "02037b26-f70f-41ef-98b2-8aef1094f0a6");
        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> lookup.loadUserByUsername("steambeta.admin.system"));
        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> lookup.loadUserByUsername("  STEAMBETA.ADMIN.SYSTEM  "));
    }
}
