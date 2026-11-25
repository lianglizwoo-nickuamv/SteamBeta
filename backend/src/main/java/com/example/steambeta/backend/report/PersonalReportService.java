package com.example.steambeta.backend.report;

import com.example.steambeta.backend.auth.AccountService;
import com.example.steambeta.backend.community.CommunityService;
import com.example.steambeta.backend.exchange.CampusExchangeService;
import com.example.steambeta.backend.report.PersonalReportDtos.*;
import com.example.steambeta.backend.task.AcademicTaskService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Servicio de generación de reportes personales del estudiante autenticado.
 * Reutiliza exclusivamente los servicios autorizados existentes sin modificar BD ni RLS.
 */
@Service
public class PersonalReportService {

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("America/Managua");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AccountService accounts;
    private final CommunityService communities;
    private final AcademicTaskService tasks;
    private final CampusExchangeService exchange;
    private final JasperReportEngine engine;

    public PersonalReportService(AccountService accounts, CommunityService communities,
                                 AcademicTaskService tasks, CampusExchangeService exchange,
                                 JasperReportEngine engine) {
        this.accounts = accounts;
        this.communities = communities;
        this.tasks = tasks;
        this.exchange = exchange;
        this.engine = engine;
    }

    // =========================================================================
    // REPORTE 1: Mi actividad académica
    // =========================================================================

    public GeneratedReport academicActivity(JasperReportEngine.Format format, LocalDate from, LocalDate to) {
        validateRange(from, to);
        var profile = accounts.fullProfile();
        UUID actorId = profile.accountId();
        String alias = safeAlias(profile.alias());

        record TimedRow(Instant ts, AcademicActivityRow row) {}
        List<TimedRow> collected = new ArrayList<>();
        long ownPosts = 0;
        long ownQuestions = 0;
        long ownAnswers = 0;
        long ownComments = 0;
        long acceptedSolutions = 0;

        for (var c : communities.mine()) {
            String commName = safeText(c.name(), "Comunidad");

            // Publicaciones propias y comentarios propios en publicaciones
            var page = communities.posts(c.id(), null, null, 50);
            for (var p : page.posts()) {
                if (actorId.equals(p.authorAccountId()) && inRange(p.createdAt(), from, to)) {
                    ownPosts++;
                    collected.add(new TimedRow(p.createdAt(), new AcademicActivityRow(
                            "Publicación", commName, safeText(p.title(), "(Sin título)"),
                            formatStatus(p.status()), formatDateTime(p.createdAt())
                    )));
                }
                if (p.commentCount() > 0) {
                    for (var cm : communities.comments(c.id(), p.id())) {
                        if (actorId.equals(cm.authorAccountId()) && inRange(cm.createdAt(), from, to)) {
                            ownComments++;
                            collected.add(new TimedRow(cm.createdAt(), new AcademicActivityRow(
                                    "Comentario", commName, truncate("En «" + safeText(p.title(), "publicación") + "»: " + safeText(cm.body(), ""), 110),
                                    formatStatus(cm.status()), formatDateTime(cm.createdAt())
                            )));
                        }
                    }
                }
            }

            // Preguntas propias, respuestas propias y soluciones aceptadas
            var qs = communities.questions(c.id(), "ALL", null, null, 50);
            for (var q : qs) {
                if (actorId.equals(q.authorAccountId()) && inRange(q.createdAt(), from, to)) {
                    ownQuestions++;
                    collected.add(new TimedRow(q.createdAt(), new AcademicActivityRow(
                            "Pregunta", commName, safeText(q.title(), "(Sin título)"),
                            q.isResolved() ? "Resuelta" : "Pendiente", formatDateTime(q.createdAt())
                    )));
                }
                if (q.commentCount() > 0) {
                    for (var ans : communities.comments(c.id(), q.id())) {
                        if (actorId.equals(ans.authorAccountId()) && inRange(ans.createdAt(), from, to)) {
                            ownAnswers++;
                            boolean isAccepted = q.resolvedCommentId() != null && q.resolvedCommentId().equals(ans.id());
                            if (isAccepted) acceptedSolutions++;
                            collected.add(new TimedRow(ans.createdAt(), new AcademicActivityRow(
                                    "Respuesta Q&A", commName, truncate("En «" + safeText(q.title(), "pregunta") + "»: " + safeText(ans.body(), ""), 110),
                                    isAccepted ? "Solución aceptada" : "Publicada", formatDateTime(ans.createdAt())
                            )));
                        }
                    }
                }
            }
        }

        collected.sort(Comparator.comparing(TimedRow::ts).reversed());
        List<AcademicActivityRow> rows = collected.stream().map(TimedRow::row).toList();

        String summary = String.format(
                "Publicaciones propias: %d  |  Preguntas propias: %d  |  Respuestas propias: %d  |  Comentarios propios: %d  |  Soluciones aceptadas: %d",
                ownPosts, ownQuestions, ownAnswers, ownComments, acceptedSolutions);
        String emptyNotice = rows.isEmpty()
                ? "No se encontró actividad académica propia registrada en el período seleccionado."
                : "Total de registros propios incluidos en este informe: " + rows.size() + ".";

        Map<String, Object> params = baseParams("Mi actividad académica", alias, from, to, summary, emptyNotice);
        byte[] bytes = engine.render("personal_academic_activity", params, rows, format, "Mi actividad academica");
        return new GeneratedReport(bytes, fileName("mi_actividad_academica", format), format.contentType());
    }

    // =========================================================================
    // REPORTE 2: Mis comunidades
    // =========================================================================

    public GeneratedReport myCommunities(JasperReportEngine.Format format, LocalDate from, LocalDate to) {
        validateRange(from, to);
        var profile = accounts.fullProfile();
        UUID actorId = profile.accountId();
        String alias = safeAlias(profile.alias());

        List<CommunityMembershipRow> rows = new ArrayList<>();
        long adminCount = 0;
        long modCount = 0;
        long memberCount = 0;

        for (var c : communities.mine()) {
            Instant joinedAt = null;
            try {
                for (var m : communities.members(c.id())) {
                    if (actorId.equals(m.accountId())) {
                        joinedAt = m.joinedAt();
                        break;
                    }
                }
            } catch (Exception ignored) {
            }

            if ((from != null || to != null) && !inRange(joinedAt, from, to)) {
                continue;
            }

            String roleCode = c.role() != null ? c.role().strip().toUpperCase(Locale.ROOT) : "MEMBER";
            switch (roleCode) {
                case "ADMIN" -> adminCount++;
                case "MODERATOR" -> modCount++;
                default -> memberCount++;
            }

            int max = c.maxMembers() > 0 ? c.maxMembers() : 50;
            rows.add(new CommunityMembershipRow(
                    safeText(c.name(), "Comunidad"),
                    formatCategory(c.category()),
                    formatVisibility(c.visibility()),
                    formatRole(roleCode),
                    "Activa",
                    joinedAt != null ? formatDate(joinedAt) : "No disponible",
                    c.memberCount() + " / " + max
            ));
        }

        String summary = String.format(
                "Comunidades activas: %d  |  Administrador: %d  |  Moderador: %d  |  Miembro: %d",
                rows.size(), adminCount, modCount, memberCount);
        String emptyNotice = rows.isEmpty()
                ? "No se encontraron membresías activas en el período consultado."
                : "Solo se muestran comunidades donde el estudiante autenticado tiene membresía activa.";

        Map<String, Object> params = baseParams("Mis comunidades", alias, from, to, summary, emptyNotice);
        byte[] bytes = engine.render("personal_communities", params, rows, format, "Mis comunidades");
        return new GeneratedReport(bytes, fileName("mis_comunidades", format), format.contentType());
    }

    // =========================================================================
    // REPORTE 3: Mi planificación académica
    // =========================================================================

    public GeneratedReport academicPlanning(JasperReportEngine.Format format, LocalDate from, LocalDate to) {
        validateRange(from, to);
        var profile = accounts.fullProfile();
        String alias = safeAlias(profile.alias());
        Instant now = Instant.now();

        List<AcademicTaskService.TaskItem> all = tasks.myTasks(null, null);
        List<PlanningTaskRow> rows = new ArrayList<>();
        long pending = 0, inProgress = 0, inReview = 0, completed = 0, overdue = 0;

        for (var t : all) {
            Instant refDate = t.dueDate() != null ? t.dueDate() : t.createdAt();
            if ((from != null || to != null) && !inRange(refDate, from, to) && !inRange(t.createdAt(), from, to)) {
                continue;
            }
            String st = t.status() != null ? t.status().strip().toUpperCase(Locale.ROOT) : "PENDING";
            switch (st) {
                case "IN_PROGRESS" -> inProgress++;
                case "IN_REVIEW" -> inReview++;
                case "COMPLETED" -> completed++;
                default -> pending++;
            }
            boolean isOverdue = t.dueDate() != null && t.dueDate().isBefore(now) && !"COMPLETED".equals(st);
            if (isOverdue) overdue++;

            String statusLabel = formatTaskStatus(st) + (isOverdue ? " (Vencida)" : "");
            rows.add(new PlanningTaskRow(
                    safeText(t.title(), "(Sin título)"),
                    truncate(safeText(t.description(), "—"), 120),
                    safeText(t.projectName(), "General"),
                    statusLabel,
                    formatPriority(t.priority()),
                    t.dueDate() != null ? formatDate(t.dueDate()) : "Sin fecha",
                    formatDate(t.createdAt())
            ));
        }

        String summary = String.format(
                "Total: %d  |  Pendientes: %d  |  En proceso: %d  |  En revisión: %d  |  Completadas: %d  |  Vencidas: %d",
                rows.size(), pending, inProgress, inReview, completed, overdue);
        String emptyNotice = rows.isEmpty()
                ? "No se encontraron tareas registradas en el período seleccionado."
                : "Las tareas vencidas se calculan únicamente a partir de fechas de vencimiento reales en estados no completados.";

        Map<String, Object> params = baseParams("Mi planificación académica", alias, from, to, summary, emptyNotice);
        byte[] bytes = engine.render("personal_academic_planning", params, rows, format, "Mi planificacion");
        return new GeneratedReport(bytes, fileName("mi_planificacion_academica", format), format.contentType());
    }

    // =========================================================================
    // REPORTE 4: Mis publicaciones de intercambio
    // =========================================================================

    public GeneratedReport exchangePublications(JasperReportEngine.Format format, LocalDate from, LocalDate to) {
        validateRange(from, to);
        var profile = accounts.fullProfile();
        UUID actorId = profile.accountId();
        String alias = safeAlias(profile.alias());

        List<CampusExchangeService.ExchangeItem> mine = exchange.list(null, true);
        List<ExchangePublicationRow> rows = new ArrayList<>();
        long exchangeCount = 0, giftCount = 0, loanCount = 0;

        for (var item : mine) {
            if (!actorId.equals(item.ownerAccountId())) continue;
            if ((from != null || to != null) && !inRange(item.createdAt(), from, to)) continue;

            String mod = item.exchangeType() != null ? item.exchangeType().strip().toUpperCase(Locale.ROOT) : "EXCHANGE";
            switch (mod) {
                case "GIFT" -> giftCount++;
                case "LOAN" -> loanCount++;
                default -> exchangeCount++;
            }

            String attSummary = "Sin adjuntos";
            if (item.attachmentCount() > 0) {
                try {
                    var atts = exchange.listAttachments(item.id());
                    String exts = atts.stream()
                            .map(a -> a.extension() != null ? a.extension().toUpperCase(Locale.ROOT) : "ARCHIVO")
                            .distinct()
                            .collect(Collectors.joining(", "));
                    attSummary = atts.size() + " adj. (" + exts + ")";
                } catch (Exception ignored) {
                    attSummary = item.attachmentCount() + " adjunto(s)";
                }
            }

            rows.add(new ExchangePublicationRow(
                    safeText(item.title(), "(Sin título)"),
                    formatExchangeCategory(item.category()),
                    formatExchangeModality(mod),
                    truncate(safeText(item.description(), "—"), 120),
                    formatExchangeStatus(item.status()),
                    attSummary,
                    formatDate(item.createdAt())
            ));
        }

        String summary = String.format(
                "Publicaciones propias: %d  |  Intercambio: %d  |  Donación: %d  |  Préstamo: %d",
                rows.size(), exchangeCount, giftCount, loanCount);
        String emptyNotice = rows.isEmpty()
                ? "No se encontraron publicaciones propias de intercambio en el período seleccionado."
                : "Intercambio solidario sin precios ni datos privados de contacto.";

        Map<String, Object> params = baseParams("Mis publicaciones de intercambio", alias, from, to, summary, emptyNotice);
        byte[] bytes = engine.render("personal_exchange_publications", params, rows, format, "Mis publicaciones");
        return new GeneratedReport(bytes, fileName("mis_publicaciones_intercambio", format), format.contentType());
    }

    // =========================================================================
    // Utilidades de validación y formateo
    // =========================================================================

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha inicial no puede ser posterior a la fecha final.");
        }
    }

    private static boolean inRange(Instant instant, LocalDate from, LocalDate to) {
        if (from == null && to == null) return true;
        if (instant == null) return false;
        LocalDate d = instant.atZone(CAMPUS_ZONE).toLocalDate();
        if (from != null && d.isBefore(from)) return false;
        if (to != null && d.isAfter(to)) return false;
        return true;
    }

    private static Map<String, Object> baseParams(String title, String alias, LocalDate from, LocalDate to,
                                                  String summary, String emptyNotice) {
        Map<String, Object> params = new HashMap<>();
        params.put("reportTitle", title);
        params.put("studentAlias", alias);
        params.put("periodLabel", formatPeriod(from, to));
        params.put("generatedAt", Instant.now().atZone(CAMPUS_ZONE).format(DATE_TIME_FMT));
        params.put("summaryLine", summary);
        params.put("emptyNotice", emptyNotice);
        return params;
    }

    private static String formatPeriod(LocalDate from, LocalDate to) {
        if (from == null && to == null) return "Histórico completo";
        if (from != null && to != null) return from.format(DATE_FMT) + " al " + to.format(DATE_FMT);
        if (from != null) return "Desde " + from.format(DATE_FMT);
        return "Hasta " + to.format(DATE_FMT);
    }

    private static String fileName(String prefix, JasperReportEngine.Format format) {
        return "steambeta_" + prefix + "_" + LocalDate.now(CAMPUS_ZONE).format(FILE_DATE_FMT) + format.extension();
    }

    private static String formatDateTime(Instant i) {
        return i == null ? "—" : i.atZone(CAMPUS_ZONE).format(DATE_TIME_FMT);
    }

    private static String formatDate(Instant i) {
        return i == null ? "—" : i.atZone(CAMPUS_ZONE).format(DATE_FMT);
    }

    private static String safeAlias(String a) {
        return (a != null && !a.isBlank()) ? a.strip() : "Estudiante";
    }

    private static String safeText(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s.strip() : fallback;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s == null ? "" : s;
        return s.substring(0, max - 1).strip() + "…";
    }

    private static String formatStatus(String st) {
        if (st == null) return "Activa";
        return switch (st.strip().toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> "Activa";
            case "HIDDEN" -> "Oculta";
            case "DELETED" -> "Eliminada";
            default -> st;
        };
    }

    private static String formatCategory(String c) {
        if (c == null || c.isBlank()) return "Académica";
        return switch (c.strip().toUpperCase(Locale.ROOT)) {
            case "ACADEMICA" -> "Académica";
            case "ESTUDIO" -> "Estudio";
            case "PROYECTO" -> "Proyectos";
            case "CARRERA" -> "Carrera";
            case "INTERES" -> "Interés";
            default -> c;
        };
    }

    private static String formatVisibility(String v) {
        if (v == null || v.isBlank()) return "Pública";
        return switch (v.strip().toUpperCase(Locale.ROOT)) {
            case "PUBLIC" -> "Pública";
            case "INVITE_ONLY" -> "Solo invitación";
            case "PRIVATE" -> "Privada";
            default -> v;
        };
    }

    private static String formatRole(String r) {
        return switch (r) {
            case "ADMIN" -> "Administrador";
            case "MODERATOR" -> "Moderador";
            default -> "Miembro";
        };
    }

    private static String formatTaskStatus(String s) {
        return switch (s) {
            case "IN_PROGRESS" -> "En proceso";
            case "IN_REVIEW" -> "En revisión";
            case "COMPLETED" -> "Completada";
            default -> "Pendiente";
        };
    }

    private static String formatPriority(String p) {
        if (p == null) return "Media";
        return switch (p.strip().toUpperCase(Locale.ROOT)) {
            case "LOW" -> "Baja";
            case "HIGH" -> "Alta";
            case "URGENT" -> "Urgente";
            default -> "Media";
        };
    }

    private static String formatExchangeCategory(String c) {
        if (c == null) return "Otros";
        return switch (c.strip().toUpperCase(Locale.ROOT)) {
            case "BOOK" -> "Libros";
            case "TOOL" -> "Herramientas";
            case "NOTES" -> "Apuntes";
            case "CALCULATOR" -> "Calculadora";
            case "LAB_EQUIPMENT" -> "Laboratorio";
            default -> "Otros";
        };
    }

    private static String formatExchangeModality(String m) {
        return switch (m) {
            case "GIFT" -> "Donación";
            case "LOAN" -> "Préstamo";
            default -> "Intercambio";
        };
    }

    private static String formatExchangeStatus(String s) {
        if (s == null) return "Disponible";
        return switch (s.strip().toUpperCase(Locale.ROOT)) {
            case "AVAILABLE" -> "Disponible";
            case "RESERVED" -> "Reservado";
            case "COMPLETED" -> "Completado";
            default -> s;
        };
    }
}
