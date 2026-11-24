package com.example.steambeta.backend.task;

import com.example.steambeta.backend.auth.AccountService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AcademicTaskService {
    public record TaskItem(
            UUID id,
            UUID accountId,
            UUID communityId,
            String title,
            String description,
            String status, // PENDING, IN_PROGRESS, IN_REVIEW, COMPLETED
            String priority, // LOW, MEDIUM, HIGH, URGENT
            Instant dueDate,
            String projectName,
            Instant createdAt
    ) {}

    public record TaskDraft(
            UUID communityId,
            String title,
            String description,
            String priority,
            String dueDateIso,
            String projectName
    ) {}

    public record TaskUpdate(
            String title,
            String description,
            String status,
            String priority,
            String dueDateIso,
            String projectName,
            Boolean clearDueDate
    ) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public AcademicTaskService(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    public List<TaskItem> myTasks(String statusFilter, String priorityFilter) {
        UUID accountId = accounts.current().accountId();
        String status = statusFilter == null || statusFilter.isBlank() ? null : statusFilter.strip().toUpperCase(Locale.ROOT);
        String priority = priorityFilter == null || priorityFilter.isBlank() ? null : priorityFilter.strip().toUpperCase(Locale.ROOT);
        return jdbc.query(
                "select * from replica.my_academic_tasks_v15(?::uuid, ?::text, ?::text)",
                (rs, row) -> mapTask(rs),
                accountId, status, priority
        );
    }

    @Transactional
    public TaskItem createTask(TaskDraft draft) {
        if (draft == null || draft.title() == null || draft.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título de la tarea es obligatorio.");
        }
        UUID accountId = accounts.current().accountId();
        String priority = draft.priority() == null ? "MEDIUM" : draft.priority().strip().toUpperCase(Locale.ROOT);
        Timestamp due = parseIsoTimestamp(draft.dueDateIso());

        return jdbc.queryForObject(
                "select * from replica.create_academic_task_v15(?::uuid, ?::text, ?::text, ?::text, ?::timestamptz, ?::text)",
                (rs, row) -> mapTask(rs),
                accountId, draft.title().strip(), draft.description(), priority, due, draft.projectName()
        );
    }

    @Transactional
    public TaskItem updateTask(UUID taskId, TaskUpdate update) {
        if (update == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos de actualización están vacíos.");
        }
        UUID accountId = accounts.current().accountId();
        Timestamp due = parseIsoTimestamp(update.dueDateIso());
        boolean clearDue = Boolean.TRUE.equals(update.clearDueDate());

        return jdbc.queryForObject(
                "select * from replica.update_academic_task_v15(?::uuid, ?::uuid, ?::text, ?::text, ?::text, ?::text, ?::timestamptz, ?::text, ?::boolean)",
                (rs, row) -> mapTask(rs),
                accountId,
                taskId,
                update.title(),
                update.description(),
                update.status() == null ? null : update.status().strip().toUpperCase(Locale.ROOT),
                update.priority() == null ? null : update.priority().strip().toUpperCase(Locale.ROOT),
                due,
                update.projectName(),
                clearDue
        );
    }

    @Transactional
    public void deleteTask(UUID taskId) {
        UUID accountId = accounts.current().accountId();
        jdbc.query("select replica.delete_academic_task_v15(?::uuid, ?::uuid)", rs -> null, accountId, taskId);
    }

    private static Timestamp parseIsoTimestamp(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Timestamp.from(Instant.parse(iso.strip()));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de fecha inválido.", ex);
        }
    }

    private static TaskItem mapTask(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaskItem(
                rs.getObject("id", UUID.class),
                rs.getObject("account_id", UUID.class),
                null,
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("priority"),
                rs.getTimestamp("due_date") == null ? null : rs.getTimestamp("due_date").toInstant(),
                rs.getString("project_name"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
