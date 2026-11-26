package com.example.steambeta.backend.admin;

import com.example.steambeta.backend.controlcenter.ControlCenterService.AuditLogEntry;
import com.example.steambeta.backend.controlcenter.ControlCenterService.StudentOverview;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Servicio de consultas y gestión administrativa sobre funciones SQL autorizadas de V11.
 * - No evade RLS ni eleva privilegios de replica_runtime.
 * - No inventa identidades ni utiliza UUID de estudiantes.
 * - Exige explícitamente una identidad técnica autorizada (STEAMBETA_ADMIN_ACTOR_ID) con rol ADMIN/GLOBAL_MODERATOR en BD.
 */
@Service
public class AdminConsoleDataService {

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("America/Managua");
    private static final int V11_MAX_SEARCH_LIMIT = 200;
    public static final String TECHNICAL_ACTOR_LOGIN = "steambeta.admin.system";
    public static final String ANONYMOUS_CONTENT_LOGIN = "steambeta.content.anonymous";

    public record AdminCapabilities(
            boolean dbAuthorized,
            String limitationReason,
            int maxDirectoryWindow,
            boolean canDeactivate,
            boolean canReactivate,
            boolean canPermanentDelete
    ) {}

    public record AdminDashboard(
            AdminCapabilities capabilities,
            long totalUsers,
            long activeUsers,
            long disabledUsers,
            long totalCommunitiesSum,
            long totalPostsSum,
            long newRegistrations30Days,
            long activeSanctionsSum,
            long recentAuditEntries,
            boolean partialWindow,
            boolean auditWindowCapReached,
            String metricsScopeNotice
    ) {}

    public record PagedUsers(
            AdminCapabilities capabilities,
            List<StudentOverview> items,
            int page,
            int size,
            int totalItems,
            int totalPages,
            boolean v11WindowCapReached
    ) {}

    public record StudentDossier(
            AdminCapabilities capabilities,
            UUID accountId,
            String alias,
            String loginIdentifier,
            String emailNotice,
            Instant createdAt,
            boolean enabled,
            String globalRole,
            String declaredUniversity,
            String declaredCareer,
            String semesterNotice,
            String bioNotice,
            String interestsNotice,
            long communitiesCount,
            long postsCount,
            long activeSanctionsCount,
            String extendedActivityNotice,
            String moderationDetailNotice,
            boolean isTechnicalActor
    ) {}

    private final JdbcTemplate jdbc;
    private final UUID technicalActorId;

    public AdminConsoleDataService(
            JdbcTemplate jdbc,
            @Value("${steambeta.admin.actor-id:}") String actorIdRaw) {
        this.jdbc = jdbc;
        UUID parsed = null;
        if (actorIdRaw != null && !actorIdRaw.isBlank()) {
            try {
                parsed = UUID.fromString(actorIdRaw.strip());
            } catch (IllegalArgumentException ignored) {
                parsed = null;
            }
        }
        this.technicalActorId = parsed;
    }

    public AdminCapabilities capabilities() {
        if (technicalActorId == null) {
            return new AdminCapabilities(
                    false,
                    "No se encuentra configurada la identidad administrativa del servidor. "
                            + "Configura el entorno administrativo antes de consultar datos institucionales.",
                    V11_MAX_SEARCH_LIMIT,
                    false,
                    false,
                    false
            );
        }
        try {
            jdbc.query("select replica.require_global_moderator(?::uuid)", rs -> null, technicalActorId);
            return new AdminCapabilities(
                    true,
                    "La consola dispone de autorización para consultar la información institucional disponible. "
                            + "Algunos datos pueden estar sujetos a restricciones de privacidad o disponibilidad.",
                    V11_MAX_SEARCH_LIMIT,
                    false,
                    false,
                    false
            );
        } catch (Exception ex) {
            return new AdminCapabilities(
                    false,
                    "La identidad administrativa configurada no está activa o no posee permisos suficientes en la base de datos.",
                    V11_MAX_SEARCH_LIMIT,
                    false,
                    false,
                    false
            );
        }
    }

    public record AuthorizedStudentWindow(List<StudentOverview> students, boolean v11WindowCapReached) {}

    public AuthorizedStudentWindow authorizedStudentWindow(String query) {
        AdminCapabilities cap = capabilities();
        if (!cap.dbAuthorized()) return new AuthorizedStudentWindow(List.of(), false);
        List<StudentOverview> rawFromDb = queryRawV11Students(query);
        boolean capReached = rawFromDb.size() >= V11_MAX_SEARCH_LIMIT;
        List<StudentOverview> filtered = rawFromDb.stream()
                .filter(s -> !isTechnicalActor(s))
                .toList();
        return new AuthorizedStudentWindow(filtered, capReached);
    }

    public List<StudentOverview> rawAuthorizedStudents(String query) {
        return authorizedStudentWindow(query).students();
    }

    private List<StudentOverview> queryRawV11Students(String query) {
        return jdbc.query("select * from replica.control_center_search_students(?::uuid, ?::text, ?::integer)",
                (rs, row) -> new StudentOverview(
                        rs.getObject("account_id", UUID.class),
                        rs.getString("login_identifier"),
                        rs.getString("alias"),
                        rs.getString("declared_university"),
                        rs.getString("declared_career"),
                        rs.getBoolean("enabled"),
                        rs.getString("global_role"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getLong("active_sanctions_count"),
                        rs.getLong("communities_count"),
                        rs.getLong("posts_count")
                ), technicalActorId, (query == null || query.isBlank()) ? null : query.strip(), V11_MAX_SEARCH_LIMIT);
    }

    public UUID technicalActorId() {
        return technicalActorId;
    }

    private boolean isTechnicalActor(StudentOverview s) {
        if (s == null) return false;
        if (technicalActorId != null && technicalActorId.equals(s.accountId())) return true;
        if (s.loginIdentifier() == null) return false;
        String clean = s.loginIdentifier().strip().toLowerCase(Locale.ROOT);
        return TECHNICAL_ACTOR_LOGIN.equals(clean)
                || ANONYMOUS_CONTENT_LOGIN.equals(clean)
                || clean.startsWith("deleted.");
    }

    public AdminDashboard dashboard() {
        AdminCapabilities cap = capabilities();
        if (!cap.dbAuthorized()) {
            return new AdminDashboard(cap, 0, 0, 0, 0, 0, -1, 0, 0, false, false, cap.limitationReason());
        }
        AuthorizedStudentWindow window = authorizedStudentWindow(null);
        List<StudentOverview> students = window.students();
        List<AuditLogEntry> logs = auditLogs(100);
        boolean auditCap = logs.size() >= 100;
        Instant thirtyDaysAgo = LocalDate.now(CAMPUS_ZONE).minusDays(30).atStartOfDay(CAMPUS_ZONE).toInstant();

        long active = students.stream().filter(StudentOverview::enabled).count();
        long disabled = students.size() - active;
        long commSum = students.stream().mapToLong(StudentOverview::communitiesCount).sum();
        long postSum = students.stream().mapToLong(StudentOverview::postsCount).sum();
        long sancSum = students.stream().mapToLong(StudentOverview::activeSanctionsCount).sum();
        // Si la ventana V11 está truncada (50 filas), NO reportar registros de 30 días como cifra exacta (-1 = limitado)
        long recentRegs = window.v11WindowCapReached()
                ? -1L
                : students.stream().filter(s -> s.createdAt() != null && !s.createdAt().isBefore(thirtyDaysAgo)).count();

        String scopeNotice = window.v11WindowCapReached()
                ? "Las estadísticas se calculan a partir de los registros disponibles en la consulta actual ("
                  + students.size() + " estudiantes). No representan necesariamente el total institucional."
                : "Estadísticas calculadas sobre " + students.size() + " estudiante(s) encontrado(s).";

        return new AdminDashboard(cap, students.size(), active, disabled, commSum, postSum, recentRegs, sancSum,
                logs.size(), window.v11WindowCapReached(), auditCap, scopeNotice);
    }

    public PagedUsers directory(String query, String statusFilter, String roleFilter, String sortBy, int page, int size) {
        AdminCapabilities cap = capabilities();
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(5, size), V11_MAX_SEARCH_LIMIT);
        if (!cap.dbAuthorized()) {
            return new PagedUsers(cap, List.of(), safePage, safeSize, 0, 0, false);
        }

        List<StudentOverview> rawFromDb = queryRawV11Students(query);
        boolean capReached = rawFromDb.size() >= V11_MAX_SEARCH_LIMIT;
        List<StudentOverview> raw = rawFromDb.stream()
                .filter(s -> !isTechnicalActor(s))
                .toList();

        String st = statusFilter == null ? "ALL" : statusFilter.strip().toUpperCase(Locale.ROOT);
        String rl = roleFilter == null ? "ALL" : roleFilter.strip().toUpperCase(Locale.ROOT);

        List<StudentOverview> filtered = raw.stream()
                .filter(u -> switch (st) {
                    case "ACTIVE" -> u.enabled();
                    case "DISABLED" -> !u.enabled();
                    default -> true;
                })
                .filter(u -> "ALL".equals(rl) || rl.equalsIgnoreCase(u.globalRole()))
                .sorted(comparatorFor(sortBy))
                .toList();

        int total = filtered.size();
        int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / safeSize);
        int fromIdx = Math.min(safePage * safeSize, total);
        int toIdx = Math.min(fromIdx + safeSize, total);

        return new PagedUsers(cap, filtered.subList(fromIdx, toIdx), safePage, safeSize, total, totalPages, capReached);
    }

    public StudentDossier dossier(UUID accountId) {
        return dossier(accountId, null);
    }

    public StudentDossier dossier(UUID accountId, String searchHint) {
        AdminCapabilities cap = capabilities();
        if (!cap.dbAuthorized()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, cap.limitationReason());
        }
        if (accountId == null || accountId.equals(technicalActorId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "student_not_found_in_v11_window");
        }
        Optional<StudentOverview> foundOpt = Optional.empty();
        if (searchHint != null && !searchHint.isBlank()) {
            foundOpt = rawAuthorizedStudents(searchHint).stream()
                    .filter(s -> accountId.equals(s.accountId()))
                    .findFirst();
        }
        if (foundOpt.isEmpty()) {
            foundOpt = rawAuthorizedStudents(null).stream()
                    .filter(s -> accountId.equals(s.accountId()))
                    .findFirst();
        }
        StudentOverview found = foundOpt
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "student_not_found"));

        // Enriquecimiento seguro del expediente mediante replica.read_full_account_profile (protegido por sesión admin)
        String emailText = "No disponible actualmente";
        String semesterText = "No declarado";
        String bioText = "No declarada";
        String interestsText = "No declarados";
        try {
            record ExtProf(String email, boolean verified, Integer semester, String bio, String interests) {}
            var extList = jdbc.query(
                    "select email, email_verified, declared_semester, bio, academic_interests from replica.read_full_account_profile(?::uuid)",
                    (rs, r) -> new ExtProf(
                            rs.getString("email"),
                            rs.getBoolean("email_verified"),
                            rs.getObject("declared_semester", Integer.class),
                            rs.getString("bio"),
                            rs.getString("academic_interests")),
                    accountId);
            if (!extList.isEmpty()) {
                var e = extList.get(0);
                if (e.email() != null && !e.email().isBlank()) {
                    emailText = e.email().strip() + (e.verified() ? " (Verificado)" : " (No verificado)");
                } else {
                    emailText = "No registrado";
                }
                if (e.semester() != null) {
                    semesterText = "Semestre " + e.semester();
                }
                if (e.bio() != null && !e.bio().isBlank()) {
                    bioText = e.bio().strip();
                }
                if (e.interests() != null && !e.interests().isBlank()) {
                    interestsText = e.interests().strip();
                }
            }
        } catch (Exception ignored) {
            // Mantener avisos naturales si el perfil extendido no está disponible
        }

        return new StudentDossier(
                cap,
                found.accountId(),
                found.alias(),
                found.loginIdentifier(),
                emailText,
                found.createdAt(),
                found.enabled(),
                found.globalRole(),
                found.declaredUniversity() != null && !found.declaredUniversity().isBlank() ? found.declaredUniversity() : "No declarada",
                found.declaredCareer() != null && !found.declaredCareer().isBlank() ? found.declaredCareer() : "No declarada",
                semesterText,
                bioText,
                interestsText,
                found.communitiesCount(),
                found.postsCount(),
                found.activeSanctionsCount(),
                "Consulta los reportes individuales inferiores para ver el detalle de actividad académica, comunidades, planificación e intercambio.",
                "Sanciones activas registradas: " + found.activeSanctionsCount()
                        + ". El historial detallado de advertencias no está disponible para esta consulta.",
                accountId.equals(technicalActorId)
        );
    }

    @Transactional
    public UUID deactivateAccount(UUID targetAccountId, String reason) {
        AdminCapabilities cap = capabilities();
        if (!cap.dbAuthorized()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, cap.limitationReason());
        }
        if (targetAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target_required");
        }
        if (targetAccountId.equals(technicalActorId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot_deactivate_technical_admin_actor");
        }
        String cleanReason = reason == null ? "" : reason.strip();
        if (cleanReason.length() < 5 || cleanReason.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo debe tener entre 5 y 2000 caracteres.");
        }
        try {
            return jdbc.queryForObject(
                    "select replica.control_center_apply_sanction(?::uuid, ?::uuid, ?::text, ?::text, ?::text, ?::integer)",
                    UUID.class,
                    technicalActorId, targetAccountId, "ACCOUNT_DEACTIVATED", cleanReason,
                    "Acción ejecutada desde SteamBeta Admin (clave compartida)", null);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo desactivar la cuenta: " + ex.getMessage());
        }
    }

    public List<AuditLogEntry> auditLogs(int limit) {
        AdminCapabilities cap = capabilities();
        if (!cap.dbAuthorized()) return List.of();
        int safeLimit = (limit < 1 || limit > 100) ? 50 : limit;
        return jdbc.query("select * from replica.control_center_audit_logs(?::uuid, ?::integer)",
                (rs, row) -> new AuditLogEntry(
                        rs.getObject("id", UUID.class),
                        rs.getString("moderator_alias"),
                        rs.getString("target_login"),
                        rs.getString("action"),
                        rs.getString("reason"),
                        rs.getTimestamp("created_at").toInstant()
                ), technicalActorId, safeLimit);
    }

    private static Comparator<StudentOverview> comparatorFor(String sortBy) {
        if (sortBy == null) return Comparator.comparing(StudentOverview::createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
        return switch (sortBy.strip().toUpperCase(Locale.ROOT)) {
            case "ALIAS_ASC" -> Comparator.comparing(s -> safe(s.alias()), String.CASE_INSENSITIVE_ORDER);
            case "LOGIN_ASC" -> Comparator.comparing(s -> safe(s.loginIdentifier()), String.CASE_INSENSITIVE_ORDER);
            case "UNIVERSITY_ASC" -> Comparator.comparing(s -> safe(s.declaredUniversity()), String.CASE_INSENSITIVE_ORDER);
            case "POSTS_DESC" -> Comparator.comparingLong(StudentOverview::postsCount).reversed();
            default -> Comparator.comparing(StudentOverview::createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
