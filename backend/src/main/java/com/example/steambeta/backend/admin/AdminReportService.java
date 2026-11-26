package com.example.steambeta.backend.admin;

import com.example.steambeta.backend.controlcenter.ControlCenterService.StudentOverview;
import com.example.steambeta.backend.report.JasperReportEngine;
import com.example.steambeta.backend.report.PersonalReportDtos.GeneratedReport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Generación del Informe General Completo de Usuarios y Expediente Individual
 * para STEAMBETA ADMIN (JasperReports 7.1.1).
 * - Protegido exclusivamente por endpoints ROLE_ADMIN_CONSOLE.
 * - Excluye siempre la identidad técnica administrativa.
 * - Enriquecido con replica.read_full_account_profile (V13),
 * replica.my_academic_tasks_v15 y
 * replica.list_exchange_items_v15 (V15) sin evadir RLS ni usar credenciales
 * DBA.
 * - Nunca incluye contraseñas, hashes, tokens ni secretos.
 */
@Service
public class AdminReportService {

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("America/Managua");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final class AdminUserRow {
        private final String groupKey;
        private final String identityBlock;
        private final String emailBlock;
        private final String profileBlock;
        private final String bioInterestsBlock;
        private final String activitySummary;
        private final String createdAtLabel;

        public AdminUserRow(String groupKey, String identityBlock, String emailBlock, String profileBlock,
                String bioInterestsBlock, String activitySummary, String createdAtLabel) {
            this.groupKey = groupKey;
            this.identityBlock = identityBlock;
            this.emailBlock = emailBlock;
            this.profileBlock = profileBlock;
            this.bioInterestsBlock = bioInterestsBlock;
            this.activitySummary = activitySummary;
            this.createdAtLabel = createdAtLabel;
        }

        public String getGroupKey() {
            return groupKey;
        }

        public String getIdentityBlock() {
            return identityBlock;
        }

        public String getEmailBlock() {
            return emailBlock;
        }

        public String getProfileBlock() {
            return profileBlock;
        }

        public String getBioInterestsBlock() {
            return bioInterestsBlock;
        }

        public String getActivitySummary() {
            return activitySummary;
        }

        public String getCreatedAtLabel() {
            return createdAtLabel;
        }
    }

    private record ExtendedProfile(
            String email,
            boolean emailVerified,
            Integer declaredSemester,
            String bio,
            String avatarUrl,
            String academicInterests) {
    }

    private final AdminConsoleDataService data;
    private final JdbcTemplate jdbc;
    private final JasperReportEngine engine;

    public AdminReportService(AdminConsoleDataService data, JdbcTemplate jdbc, JasperReportEngine engine) {
        this.data = data;
        this.jdbc = jdbc;
        this.engine = engine;
    }

    /**
     * Único Informe General Completo de Usuarios para STEAMBETA ADMIN (PDF y XLSX).
     */
    public GeneratedReport generateFullUsersReport(JasperReportEngine.Format format, String query) {
        var cap = data.capabilities();
        if (!cap.dbAuthorized()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, cap.limitationReason());
        }

        var window = data.authorizedStudentWindow(query);
        boolean partialV11 = window.v11WindowCapReached();
        List<StudentOverview> students = window.students();

        List<AdminUserRow> rows = new ArrayList<>();
        long totalComms = 0, totalPosts = 0, totalTasks = 0, totalExchanges = 0;

        for (var s : students) {
            ExtendedProfile ext = readExtendedProfileSafe(s.accountId());
            long tasksCount = countTasksSafe(s.accountId());
            long exchangesCount = countExchangesSafe(s.accountId());

            totalComms += s.communitiesCount();
            totalPosts += s.postsCount();
            totalTasks += tasksCount;
            totalExchanges += exchangesCount;

            String identityBlock = safe(s.alias(), "Sin alias")
                    + "\nLogin: " + safe(s.loginIdentifier(), "—")
                    + "\nRol: " + safe(s.globalRole(), "USER")
                    + "\nUUID: " + s.accountId();

            String emailBlock;
            if (ext != null && ext.email() != null && !ext.email().isBlank()) {
                emailBlock = ext.email().strip() + "\n" + (ext.emailVerified() ? "Verificado" : "No verificado");
            } else {
                emailBlock = "Sin correo registrado\nNo verificado";
            }

            String semText = (ext != null && ext.declaredSemester() != null)
                    ? "Semestre: " + ext.declaredSemester()
                    : "Semestre: No declarado";
            String avatarText = (ext != null && ext.avatarUrl() != null && !ext.avatarUrl().isBlank())
                    ? "Avatar: Registrado"
                    : "Avatar: Sin imagen";
            String profileBlock = "Univ.: " + safe(s.declaredUniversity(), "No declarada")
                    + "\nCarrera: " + safe(s.declaredCareer(), "No declarada")
                    + "\n" + semText + " · " + avatarText;

            String bioText = (ext != null && ext.bio() != null && !ext.bio().isBlank())
                    ? truncate(ext.bio().strip(), 95)
                    : "Sin biografía";
            String intText = (ext != null && ext.academicInterests() != null && !ext.academicInterests().isBlank())
                    ? truncate(ext.academicInterests().strip(), 95)
                    : "Sin intereses";
            String bioInterestsBlock = "Bio: " + bioText + "\nIntereses: " + intText;

            String activitySummary = "Comunidades: " + s.communitiesCount()
                    + "\nPublicaciones: " + s.postsCount()
                    + "\nTareas: " + tasksCount
                    + "\nIntercambios: " + exchangesCount;

            String createdLabel = s.createdAt() != null
                    ? s.createdAt().atZone(CAMPUS_ZONE).format(DATE_FMT)
                    : "—";

            String groupKey = partialV11
                    ? "MUESTRA PARCIAL DE USUARIOS (ALCANCE DE CONSULTA LIMITADO)"
                    : "USUARIOS ESTUDIANTILES ENCONTRADOS";

            rows.add(new AdminUserRow(groupKey, identityBlock, emailBlock, profileBlock,
                    bioInterestsBlock, activitySummary, createdLabel));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("reportTitle", "Informe General de Usuarios");
        params.put("reportType", partialV11 ? "Muestra parcial" : "Resultados para el filtro");
        params.put("filtersApplied",
                (query != null && !query.isBlank()) ? "Búsqueda='" + query.strip() + "'" : "Sin filtro de búsqueda");
        params.put("generatedAt", Instant.now().atZone(CAMPUS_ZONE).format(DATE_TIME_FMT));
        params.put("summaryLine", String.format(
                "%s · Usuarios incluidos: %d · Comunidades: %d · Publicaciones: %d · Tareas: %d · Intercambios: %d",
                partialV11 ? "MUESTRA PARCIAL (no incluye necesariamente el total institucional)"
                        : "RESULTADOS PARA EL FILTRO APLICADO",
                students.size(), totalComms, totalPosts, totalTasks, totalExchanges));
        params.put("emptyNotice", rows.isEmpty()
                ? "No existen usuarios estudiantiles que coincidan con el filtro aplicado."
                : (partialV11
                        ? "AVISO DE ALCANCE: Este documento incluye " + students.size()
                                + " estudiantes obtenidos dentro del alcance máximo de la consulta actual y no representa necesariamente el total institucional. "
                                + "Algunos indicadores agregados (preguntas Q&A, recursos comunitarios y oportunidades por autor) no están disponibles en esta vista resumida."
                        : "Resultados correspondientes al filtro aplicado (" + students.size()
                                + " estudiantes). Algunos indicadores agregados (preguntas Q&A, recursos comunitarios y oportunidades por autor) no están disponibles en esta vista resumida."));

        byte[] bytes = engine.render("admin/admin_directory_report", params, rows, format, "Informe General Usuarios");
        String fname = "steambeta_admin_informe_usuarios_"
                + LocalDate.now(CAMPUS_ZONE).format(FILE_DATE_FMT) + format.extension();
        return new GeneratedReport(bytes, fname, format.contentType());
    }

    public GeneratedReport generateInstitutional(String kind, JasperReportEngine.Format format,
            String query, LocalDate from, LocalDate to) {
        return generateFullUsersReport(format, query);
    }

    public GeneratedReport generateDossier(UUID accountId, JasperReportEngine.Format format) {
        return generateDossier(accountId, format, null);
    }

    public GeneratedReport generateDossier(UUID accountId, JasperReportEngine.Format format, String searchHint) {
        var d = data.dossier(accountId, searchHint);
        Map<String, Object> params = new HashMap<>();
        params.put("generatedAt", Instant.now().atZone(CAMPUS_ZONE).format(DATE_TIME_FMT));
        params.put("alias", safe(d.alias(), "Estudiante"));
        params.put("loginIdentifier", safe(d.loginIdentifier(), "—"));
        params.put("emailNotice", d.emailNotice());
        params.put("createdAtLabel",
                d.createdAt() != null ? d.createdAt().atZone(CAMPUS_ZONE).format(DATE_TIME_FMT) : "—");
        params.put("statusLabel", d.enabled() ? "Activa" : "Desactivada");
        params.put("roleLabel", safe(d.globalRole(), "USER"));
        params.put("university", d.declaredUniversity());
        params.put("career", d.declaredCareer());
        params.put("semesterNotice", d.semesterNotice());
        params.put("bioNotice", d.bioNotice());
        params.put("interestsNotice", d.interestsNotice());
        params.put("communitiesCount", String.valueOf(d.communitiesCount()));
        params.put("postsCount", String.valueOf(d.postsCount()));
        params.put("activeSanctionsCount", String.valueOf(d.activeSanctionsCount()));
        params.put("extendedActivityNotice", d.extendedActivityNotice());
        params.put("moderationDetailNotice", d.moderationDetailNotice());

        byte[] bytes = engine.render("admin/admin_student_dossier", params, List.of(), format, "Expediente");
        String fname = "steambeta_admin_expediente_" + safe(d.loginIdentifier(), "usuario") + "_"
                + LocalDate.now(CAMPUS_ZONE).format(FILE_DATE_FMT) + format.extension();
        return new GeneratedReport(bytes, fname, format.contentType());
    }

    private ExtendedProfile readExtendedProfileSafe(UUID accountId) {
        try {
            var list = jdbc.query(
                    "select email, email_verified, declared_semester, bio, avatar_url, academic_interests from replica.read_full_account_profile(?::uuid)",
                    (rs, r) -> new ExtendedProfile(
                            rs.getString("email"),
                            rs.getBoolean("email_verified"),
                            rs.getObject("declared_semester", Integer.class),
                            rs.getString("bio"),
                            rs.getString("avatar_url"),
                            rs.getString("academic_interests")),
                    accountId);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long countTasksSafe(UUID accountId) {
        try {
            Long c = jdbc.queryForObject(
                    "select count(*) from replica.my_academic_tasks_v15(?::uuid, null, null)",
                    Long.class, accountId);
            return c != null ? c : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private long countExchangesSafe(UUID accountId) {
        try {
            Long c = jdbc.queryForObject(
                    "select count(*) from replica.list_exchange_items_v15(?::uuid, null, true)",
                    Long.class, accountId);
            return c != null ? c : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String safe(String s, String fb) {
        return (s != null && !s.isBlank()) ? s.strip() : fb;
    }

    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max - 1) + "…";
    }
}
