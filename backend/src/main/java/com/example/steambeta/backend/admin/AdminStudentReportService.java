package com.example.steambeta.backend.admin;

import com.example.steambeta.backend.report.JasperReportEngine;
import com.example.steambeta.backend.report.PersonalReportDtos.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Servicio administrativo dedicado a generar los 4 reportes detallados de un estudiante seleccionado
 * desde el Expediente Individual de STEAMBETA ADMIN.
 * - Valida que exista sesión administrativa (en controller) y actor técnico V11 autorizado.
 * - Recibe explícitamente el targetAccountId del estudiante seleccionado (jamás usa el actor técnico).
 * - Utiliza exclusivamente funciones SQL SECURITY DEFINER existentes de V1–V17 sin evadir RLS ni usar DBA.
 * - Conserva las 4 plantillas JRXML diferenciadas y soporta PDF y XLSX.
 */
@Service
public class AdminStudentReportService {

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("America/Managua");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbc;
    private final AdminConsoleDataService adminData;
    private final JasperReportEngine engine;

    public AdminStudentReportService(JdbcTemplate jdbc, AdminConsoleDataService adminData, JasperReportEngine engine) {
        this.jdbc = jdbc;
        this.adminData = adminData;
        this.engine = engine;
    }

    public GeneratedReport generateForStudent(UUID targetAccountId, String reportKind,
                                              JasperReportEngine.Format format,
                                              LocalDate from, LocalDate to, String searchHint) {
        validateRange(from, to);
        // Verifica dbAuthorized, excluye el actor técnico (404) y obtiene identidad real del estudiante seleccionado
        var dossier = adminData.dossier(targetAccountId, searchHint);
        String studentLabel = safeText(dossier.alias(), "Estudiante")
                + " (" + safeText(dossier.loginIdentifier(), "sin-login") + ")";
        String fileSlug = safeSlug(dossier.loginIdentifier());

        String normalized = reportKind == null ? "" : reportKind.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "academic-activity" -> academicActivity(targetAccountId, studentLabel, fileSlug, format, from, to);
            case "communities" -> communities(targetAccountId, studentLabel, fileSlug, format, from, to);
            case "academic-planning" -> academicPlanning(targetAccountId, studentLabel, fileSlug, format, from, to);
            case "exchange-publications" -> exchangePublications(targetAccountId, studentLabel, fileSlug, format, from, to);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de reporte estudiantil no soportado: " + reportKind);
        };
    }

    // =========================================================================
    // 1. Actividad académica del estudiante seleccionado
    // =========================================================================

    private GeneratedReport academicActivity(UUID targetId, String studentLabel, String fileSlug,
                                             JasperReportEngine.Format format, LocalDate from, LocalDate to) {
        record CommRef(UUID id, String name) {}
        record PostRef(UUID id, UUID authorId, String title, String status, Instant createdAt, long commentCount) {}
        record QuestionRef(UUID id, UUID authorId, String title, Instant createdAt, long commentCount, boolean resolved, UUID resolvedCommentId) {}
        record CommentRef(UUID id, UUID authorId, String body, String status, Instant createdAt) {}
        record TimedRow(Instant ts, AcademicActivityRow row) {}

        List<CommRef> comms = jdbc.query("select id, name from replica.my_communities_v14(?::uuid)",
                (rs, r) -> new CommRef(rs.getObject("id", UUID.class), rs.getString("name")), targetId);

        List<TimedRow> collected = new ArrayList<>();
        long ownPosts = 0, ownQuestions = 0, ownAnswers = 0, ownComments = 0, acceptedSolutions = 0;

        for (var c : comms) {
            String commName = safeText(c.name(), "Comunidad");

            List<PostRef> posts = jdbc.query(
                    "select id, author_account_id, title, status, created_at, comment_count from replica.community_posts(?, ?, ?, ?, ?)",
                    (rs, r) -> new PostRef(
                            rs.getObject("id", UUID.class),
                            rs.getObject("author_account_id", UUID.class),
                            rs.getString("title"),
                            rs.getString("status"),
                            ts(rs.getTimestamp("created_at")),
                            rs.getLong("comment_count")),
                    targetId, c.id(), null, null, 50);

            for (var p : posts) {
                if (targetId.equals(p.authorId()) && inRange(p.createdAt(), from, to)) {
                    ownPosts++;
                    collected.add(new TimedRow(p.createdAt(), new AcademicActivityRow(
                            "Publicación", commName, safeText(p.title(), "(Sin título)"),
                            formatStatus(p.status()), formatDateTime(p.createdAt()))));
                }
                if (p.commentCount() > 0) {
                    List<CommentRef> comments = jdbc.query(
                            "select id, author_account_id, body, status, created_at from replica.community_comments(?, ?, ?)",
                            (rs, r) -> new CommentRef(
                                    rs.getObject("id", UUID.class),
                                    rs.getObject("author_account_id", UUID.class),
                                    rs.getString("body"),
                                    rs.getString("status"),
                                    ts(rs.getTimestamp("created_at"))),
                            targetId, c.id(), p.id());
                    for (var cm : comments) {
                        if (targetId.equals(cm.authorId()) && inRange(cm.createdAt(), from, to)) {
                            ownComments++;
                            collected.add(new TimedRow(cm.createdAt(), new AcademicActivityRow(
                                    "Comentario", commName,
                                    truncate("En «" + safeText(p.title(), "publicación") + "»: " + safeText(cm.body(), ""), 110),
                                    formatStatus(cm.status()), formatDateTime(cm.createdAt()))));
                        }
                    }
                }
            }

            List<QuestionRef> questions = jdbc.query(
                    "select id, author_account_id, title, created_at, comment_count, is_resolved, resolved_comment_id from replica.community_questions(?, ?, ?, ?, ?, ?)",
                    (rs, r) -> new QuestionRef(
                            rs.getObject("id", UUID.class),
                            rs.getObject("author_account_id", UUID.class),
                            rs.getString("title"),
                            ts(rs.getTimestamp("created_at")),
                            rs.getLong("comment_count"),
                            rs.getBoolean("is_resolved"),
                            rs.getObject("resolved_comment_id", UUID.class)),
                    targetId, c.id(), "ALL", null, null, 50);

            for (var q : questions) {
                if (targetId.equals(q.authorId()) && inRange(q.createdAt(), from, to)) {
                    ownQuestions++;
                    collected.add(new TimedRow(q.createdAt(), new AcademicActivityRow(
                            "Pregunta", commName, safeText(q.title(), "(Sin título)"),
                            q.resolved() ? "Resuelta" : "Pendiente", formatDateTime(q.createdAt()))));
                }
                if (q.commentCount() > 0) {
                    List<CommentRef> answers = jdbc.query(
                            "select id, author_account_id, body, status, created_at from replica.community_comments(?, ?, ?)",
                            (rs, r) -> new CommentRef(
                                    rs.getObject("id", UUID.class),
                                    rs.getObject("author_account_id", UUID.class),
                                    rs.getString("body"),
                                    rs.getString("status"),
                                    ts(rs.getTimestamp("created_at"))),
                            targetId, c.id(), q.id());
                    for (var ans : answers) {
                        if (targetId.equals(ans.authorId()) && inRange(ans.createdAt(), from, to)) {
                            ownAnswers++;
                            boolean isAccepted = q.resolvedCommentId() != null && q.resolvedCommentId().equals(ans.id());
                            if (isAccepted) acceptedSolutions++;
                            collected.add(new TimedRow(ans.createdAt(), new AcademicActivityRow(
                                    "Respuesta Q&A", commName,
                                    truncate("En «" + safeText(q.title(), "pregunta") + "»: " + safeText(ans.body(), ""), 110),
                                    isAccepted ? "Solución aceptada" : "Publicada", formatDateTime(ans.createdAt()))));
                        }
                    }
                }
            }
        }

        collected.sort(Comparator.comparing(TimedRow::ts, Comparator.nullsLast(Comparator.reverseOrder())));
        List<AcademicActivityRow> rows = collected.stream().map(TimedRow::row).toList();

        String summary = String.format(
                "Reporte emitido desde STEAMBETA ADMIN · Publicaciones: %d · Preguntas: %d · Respuestas Q&A: %d · Comentarios: %d · Soluciones aceptadas: %d",
                ownPosts, ownQuestions, ownAnswers, ownComments, acceptedSolutions);
        String emptyNotice = rows.isEmpty()
                ? "El estudiante seleccionado no registra actividad académica en el período consultado."
                : "Total de registros del estudiante incluidos: " + rows.size() + ".";

        Map<String, Object> params = baseParams("Actividad académica del estudiante", studentLabel, from, to, summary, emptyNotice);
        putKpis(params,
                "PUBLICACIONES", String.valueOf(ownPosts),
                "PREGUNTAS", String.valueOf(ownQuestions),
                "RESPUESTAS Q&A", String.valueOf(ownAnswers),
                "SOLUCIONES ACEPT.", String.valueOf(acceptedSolutions));

        List<ChartMetric> activitySeries = List.of(
                new ChartMetric("Actividad", "Publicaciones", ownPosts),
                new ChartMetric("Actividad", "Preguntas", ownQuestions),
                new ChartMetric("Actividad", "Respuestas", ownAnswers),
                new ChartMetric("Actividad", "Comentarios", ownComments),
                new ChartMetric("Actividad", "Soluciones", acceptedSolutions));
        Map<String, Long> byCommunity = collected.stream()
                .collect(Collectors.groupingBy(tr -> tr.row().getCommunityName(), LinkedHashMap::new, Collectors.counting()));
        List<ChartMetric> communityPie = byCommunity.isEmpty()
                ? List.of(new ChartMetric("Comunidades", "Sin registros en el período", 1))
                : byCommunity.entrySet().stream().map(e -> new ChartMetric("Comunidades", e.getKey(), e.getValue())).toList();
        params.put("activityChartData", new JRBeanCollectionDataSource(activitySeries));
        params.put("communityChartData", new JRBeanCollectionDataSource(communityPie));

        byte[] bytes = engine.render("personal_academic_activity", params, rows, format, "Actividad academica");
        return new GeneratedReport(bytes, fileName("actividad_academica_" + fileSlug, format), format.contentType());
    }

    // =========================================================================
    // 2. Comunidades del estudiante seleccionado
    // =========================================================================

    private GeneratedReport communities(UUID targetId, String studentLabel, String fileSlug,
                                        JasperReportEngine.Format format, LocalDate from, LocalDate to) {
        record CommRow(UUID id, String name, String category, String visibility, String role, long memberCount, int maxMembers) {}

        List<CommRow> comms = jdbc.query("select id, name, category, visibility, role, member_count, max_members from replica.my_communities_v14(?::uuid)",
                (rs, r) -> new CommRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("visibility"),
                        rs.getString("role"),
                        rs.getLong("member_count"),
                        rs.getInt("max_members")),
                targetId);

        List<CommunityMembershipRow> rows = new ArrayList<>();
        long adminCount = 0, modCount = 0, memberCount = 0;

        for (var c : comms) {
            Instant joinedAt = null;
            try {
                List<Instant> joinedList = jdbc.query(
                        "select joined_at from replica.community_members(?, ?) where account_id = ?",
                        (rs, r) -> ts(rs.getTimestamp("joined_at")),
                        targetId, c.id(), targetId);
                if (!joinedList.isEmpty()) joinedAt = joinedList.get(0);
            } catch (Exception ignored) {}

            if ((from != null || to != null) && !inRange(joinedAt, from, to)) continue;

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
                    c.memberCount() + " / " + max));
        }

        rows.sort(Comparator.comparing(CommunityMembershipRow::getCategory, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CommunityMembershipRow::getName, String.CASE_INSENSITIVE_ORDER));

        String summary = String.format(
                "Reporte emitido desde STEAMBETA ADMIN · Comunidades activas: %d · Administrador: %d · Moderador: %d · Miembro: %d",
                rows.size(), adminCount, modCount, memberCount);
        String emptyNotice = rows.isEmpty()
                ? "El estudiante seleccionado no registra membresías activas en el período consultado."
                : "Membresías activas verificadas en PostgreSQL para el estudiante seleccionado.";

        Map<String, Object> params = baseParams("Comunidades del estudiante", studentLabel, from, to, summary, emptyNotice);
        putKpis(params,
                "COMUNIDADES ACTIVAS", String.valueOf(rows.size()),
                "ADMINISTRADOR", String.valueOf(adminCount),
                "MODERADOR", String.valueOf(modCount),
                "MIEMBRO", String.valueOf(memberCount));
        byte[] bytes = engine.render("personal_communities", params, rows, format, "Comunidades");
        return new GeneratedReport(bytes, fileName("comunidades_" + fileSlug, format), format.contentType());
    }

    // =========================================================================
    // 3. Planificación académica del estudiante seleccionado
    // =========================================================================

    private GeneratedReport academicPlanning(UUID targetId, String studentLabel, String fileSlug,
                                             JasperReportEngine.Format format, LocalDate from, LocalDate to) {
        record TaskRow(String title, String description, String projectName, String status, String priority, Instant dueDate, Instant createdAt) {}

        List<TaskRow> all = jdbc.query("select title, description, project_name, status, priority, due_date, created_at from replica.my_academic_tasks_v15(?::uuid, ?::text, ?::text)",
                (rs, r) -> new TaskRow(
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("project_name"),
                        rs.getString("status"),
                        rs.getString("priority"),
                        ts(rs.getTimestamp("due_date")),
                        ts(rs.getTimestamp("created_at"))),
                targetId, null, null);

        Instant now = Instant.now();
        List<PlanningTaskRow> rows = new ArrayList<>();
        long pending = 0, inProgress = 0, inReview = 0, completed = 0, overdue = 0;

        for (var t : all) {
            Instant refDate = t.dueDate() != null ? t.dueDate() : t.createdAt();
            if ((from != null || to != null) && !inRange(refDate, from, to) && !inRange(t.createdAt(), from, to)) continue;

            String st = t.status() != null ? t.status().strip().toUpperCase(Locale.ROOT) : "PENDING";
            switch (st) {
                case "IN_PROGRESS" -> inProgress++;
                case "IN_REVIEW" -> inReview++;
                case "COMPLETED" -> completed++;
                default -> pending++;
            }
            boolean isOverdue = t.dueDate() != null && t.dueDate().isBefore(now) && !"COMPLETED".equals(st);
            if (isOverdue) overdue++;

            rows.add(new PlanningTaskRow(
                    safeText(t.title(), "Tarea académica"),
                    safeText(t.description(), "Sin descripción"),
                    safeText(t.projectName(), "Sin materia asignada"),
                    formatTaskStatus(st) + (isOverdue ? " (Vencida)" : ""),
                    formatPriority(t.priority()),
                    t.dueDate() != null ? formatDateTime(t.dueDate()) : "Sin fecha límite",
                    formatDate(t.createdAt())));
        }

        long total = rows.size();
        int pct = total > 0 ? (int) Math.round((completed * 100.0) / total) : 0;
        String summary = String.format(
                "Reporte emitido desde STEAMBETA ADMIN · Total: %d · Pendientes: %d · En proceso: %d · En revisión: %d · Completadas: %d (%d%%) · Vencidas: %d",
                total, pending, inProgress, inReview, completed, pct, overdue);
        String emptyNotice = rows.isEmpty()
                ? "El estudiante seleccionado no registra tareas académicas en el período consultado."
                : "Planificación académica consultada con autorización institucional.";

        Map<String, Object> params = baseParams("Planificación académica del estudiante", studentLabel, from, to, summary, emptyNotice);
        putKpis(params,
                "TOTAL TAREAS", String.valueOf(total),
                "PENDIENTES", String.valueOf(pending + inProgress + inReview),
                "COMPLETADAS", completed + " (" + pct + "%)",
                "VENCIDAS", String.valueOf(overdue));

        List<ChartMetric> taskSeries = List.of(
                new ChartMetric("Tareas", "Pendientes", pending),
                new ChartMetric("Tareas", "En proceso", inProgress),
                new ChartMetric("Tareas", "En revisión", inReview),
                new ChartMetric("Tareas", "Completadas", completed),
                new ChartMetric("Tareas", "Vencidas", overdue));
        params.put("taskChartData", new JRBeanCollectionDataSource(taskSeries));

        byte[] bytes = engine.render("personal_academic_planning", params, rows, format, "Planificacion academica");
        return new GeneratedReport(bytes, fileName("planificacion_" + fileSlug, format), format.contentType());
    }

    // =========================================================================
    // 4. Publicaciones de intercambio del estudiante seleccionado
    // =========================================================================

    private GeneratedReport exchangePublications(UUID targetId, String studentLabel, String fileSlug,
                                                 JasperReportEngine.Format format, LocalDate from, LocalDate to) {
        record ExchRow(String title, String description, String category, String exchangeType, String status, String contactInfo, Instant createdAt, long attachmentCount) {}

        List<ExchRow> all = jdbc.query(
                "select title, description, category, exchange_type, status, contact_info, created_at, attachment_count from replica.list_exchange_items_v15(?::uuid, ?::text, ?::boolean)",
                (rs, r) -> new ExchRow(
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("category"),
                        rs.getString("exchange_type"),
                        rs.getString("status"),
                        rs.getString("contact_info"),
                        ts(rs.getTimestamp("created_at")),
                        rs.getLong("attachment_count")),
                targetId, null, true);

        List<ExchangePublicationRow> rows = new ArrayList<>();
        long available = 0, reserved = 0, completed = 0;

        for (var item : all) {
            if ((from != null || to != null) && !inRange(item.createdAt(), from, to)) continue;
            String st = item.status() != null ? item.status().strip().toUpperCase(Locale.ROOT) : "AVAILABLE";
            switch (st) {
                case "RESERVED" -> reserved++;
                case "COMPLETED" -> completed++;
                default -> available++;
            }
            String attSummary = item.attachmentCount() > 0
                    ? item.attachmentCount() + " adjunto(s) · Contacto: " + safeText(item.contactInfo(), "Sin contacto")
                    : "Sin adjuntos · Contacto: " + safeText(item.contactInfo(), "Sin contacto");
            rows.add(new ExchangePublicationRow(
                    safeText(item.title(), "Material académico"),
                    formatExchangeCategory(item.category()),
                    formatExchangeType(item.exchangeType()),
                    safeText(item.description(), "Sin descripción"),
                    formatExchangeStatus(st),
                    attSummary,
                    formatDate(item.createdAt())));
        }

        String summary = String.format(
                "Reporte emitido desde STEAMBETA ADMIN · Publicaciones: %d · Disponibles: %d · Reservadas: %d · Completadas: %d",
                rows.size(), available, reserved, completed);
        String emptyNotice = rows.isEmpty()
                ? "El estudiante seleccionado no registra publicaciones en Intercambio Universitario para el período consultado."
                : "Publicaciones solidarias consultadas con autorización institucional.";

        Map<String, Object> params = baseParams("Publicaciones de intercambio del estudiante", studentLabel, from, to, summary, emptyNotice);
        putKpis(params,
                "PUBLICACIONES", String.valueOf(rows.size()),
                "DISPONIBLES", String.valueOf(available),
                "RESERVADAS", String.valueOf(reserved),
                "COMPLETADAS", String.valueOf(completed));

        byte[] bytes = engine.render("personal_exchange_publications", params, rows, format, "Publicaciones intercambio");
        return new GeneratedReport(bytes, fileName("intercambio_" + fileSlug, format), format.contentType());
    }

    // =========================================================================
    // Utilidades
    // =========================================================================

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha inicial no puede ser posterior a la fecha final.");
        }
    }

    private static Map<String, Object> baseParams(String title, String studentName, LocalDate from, LocalDate to,
                                                  String summary, String emptyNotice) {
        Map<String, Object> params = new HashMap<>();
        params.put("reportTitle", title);
        params.put("studentName", studentName);
        params.put("dateRange", formatRange(from, to));
        params.put("generatedAt", Instant.now().atZone(CAMPUS_ZONE).format(DATE_TIME_FMT));
        params.put("summaryLine", summary);
        params.put("emptyNotice", emptyNotice);
        return params;
    }

    private static void putKpis(Map<String, Object> params, String l1, String v1, String l2, String v2,
                                String l3, String v3, String l4, String v4) {
        params.put("kpi1Label", l1); params.put("kpi1Value", v1);
        params.put("kpi2Label", l2); params.put("kpi2Value", v2);
        params.put("kpi3Label", l3); params.put("kpi3Value", v3);
        params.put("kpi4Label", l4); params.put("kpi4Value", v4);
    }

    private static Instant ts(Timestamp t) { return t != null ? t.toInstant() : null; }

    private static boolean inRange(Instant instant, LocalDate from, LocalDate to) {
        if (from == null && to == null) return true;
        if (instant == null) return false;
        LocalDate d = instant.atZone(CAMPUS_ZONE).toLocalDate();
        if (from != null && d.isBefore(from)) return false;
        if (to != null && d.isAfter(to)) return false;
        return true;
    }

    private static String formatRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) return "Histórico completo";
        if (from != null && to != null) return from.format(DATE_FMT) + " – " + to.format(DATE_FMT);
        return from != null ? "Desde " + from.format(DATE_FMT) : "Hasta " + to.format(DATE_FMT);
    }

    private static String formatDateTime(Instant i) { return i != null ? i.atZone(CAMPUS_ZONE).format(DATE_TIME_FMT) : "—"; }
    private static String formatDate(Instant i) { return i != null ? i.atZone(CAMPUS_ZONE).format(DATE_FMT) : "—"; }
    private static String safeText(String s, String fb) { return (s != null && !s.isBlank()) ? s.strip() : fb; }
    private static String safeSlug(String s) { return (s != null && !s.isBlank()) ? s.strip().replaceAll("[^a-zA-Z0-9._-]", "_") : "estudiante"; }
    private static String truncate(String s, int max) { return (s == null || s.length() <= max) ? s : s.substring(0, max - 1) + "…"; }
    private static String fileName(String prefix, JasperReportEngine.Format f) {
        return "steambeta_admin_" + prefix + "_" + LocalDate.now(CAMPUS_ZONE).format(FILE_DATE_FMT) + f.extension();
    }

    private static String formatStatus(String s) {
        if (s == null) return "Activa";
        return switch (s.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> "Activa";
            case "HIDDEN" -> "Oculta";
            case "DELETED" -> "Eliminada";
            default -> s;
        };
    }

    private static String formatCategory(String c) {
        if (c == null) return "Académica";
        return switch (c.toUpperCase(Locale.ROOT)) {
            case "STUDY" -> "Estudio";
            case "PROJECT" -> "Proyecto";
            case "CAREER" -> "Carrera";
            case "INTEREST" -> "Interés";
            default -> "Académica";
        };
    }

    private static String formatVisibility(String v) {
        if (v == null) return "Pública";
        return switch (v.toUpperCase(Locale.ROOT)) {
            case "INVITE_ONLY" -> "Solo invitación";
            case "PRIVATE" -> "Privada";
            default -> "Pública";
        };
    }

    private static String formatRole(String r) {
        return switch (r) {
            case "ADMIN" -> "Administrador";
            case "MODERATOR" -> "Moderador";
            default -> "Miembro";
        };
    }

    private static String formatPriority(String p) {
        if (p == null) return "Media";
        return switch (p.toUpperCase(Locale.ROOT)) {
            case "URGENT" -> "Urgente";
            case "HIGH" -> "Alta";
            case "LOW" -> "Baja";
            default -> "Media";
        };
    }

    private static String formatTaskStatus(String st) {
        return switch (st) {
            case "IN_PROGRESS" -> "En proceso";
            case "IN_REVIEW" -> "En revisión";
            case "COMPLETED" -> "Completada";
            default -> "Pendiente";
        };
    }

    private static String formatExchangeCategory(String c) {
        if (c == null) return "Otro";
        return switch (c.toUpperCase(Locale.ROOT)) {
            case "BOOK" -> "Libro";
            case "NOTES" -> "Apuntes";
            case "TOOL" -> "Herramienta";
            case "CALCULATOR" -> "Calculadora";
            case "LAB_EQUIPMENT" -> "Equipo de lab.";
            default -> "Otro";
        };
    }

    private static String formatExchangeType(String t) {
        if (t == null) return "Intercambio";
        return switch (t.toUpperCase(Locale.ROOT)) {
            case "GIFT" -> "Donación";
            case "LOAN" -> "Préstamo";
            default -> "Intercambio";
        };
    }

    private static String formatExchangeStatus(String s) {
        return switch (s) {
            case "RESERVED" -> "Reservado";
            case "COMPLETED" -> "Completado";
            default -> "Disponible";
        };
    }
}
