package com.example.steambeta.backend.task;

import com.example.steambeta.backend.auth.AccountService;
import java.time.Instant;
import java.sql.Timestamp;
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
            Instant dueDate,
            String projectName
    ) {}

    public record TaskUpdate(
            String status,
            String priority,
            Instant dueDate
    ) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public AcademicTaskService(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    public List<TaskItem> myTasks(String statusFilter) {
        UUID accountId = accounts.current().accountId();
        String sql = "select id, account_id, community_id, title, description, status, priority, due_date, project_name, created_at " +
                "from replica.academic_task where account_id = ? " +
                (statusFilter != null && !statusFilter.isBlank() ? "and status = ? " : "") +
                "order by due_date asc nulls last, created_at desc";
        try {
            if (statusFilter != null && !statusFilter.isBlank()) {
                return jdbc.query(sql, (rs, row) -> mapTask(rs), accountId, statusFilter.strip().toUpperCase(Locale.ROOT));
            } else {
                return jdbc.query(sql, (rs, row) -> mapTask(rs), accountId);
            }
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Transactional
    public TaskItem createTask(TaskDraft draft) {
        if (draft == null || draft.title() == null || draft.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_title");
        }
        UUID accountId = accounts.current().accountId();
        String priority = draft.priority() == null ? "MEDIUM" : draft.priority().strip().toUpperCase(Locale.ROOT);
        Timestamp due = draft.dueDate() == null ? null : Timestamp.from(draft.dueDate());

        UUID newId = UUID.randomUUID();
        try {
            jdbc.update("insert into replica.academic_task(id, account_id, community_id, title, description, status, priority, due_date, project_name) " +
                    "values (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)",
                    newId, accountId, draft.communityId(), draft.title().strip(), draft.description(),
                    priority, due, draft.projectName());

            return new TaskItem(newId, accountId, draft.communityId(), draft.title().strip(),
                    draft.description(), "PENDING", priority, draft.dueDate(), draft.projectName(), Instant.now());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "could_not_create_task");
        }
    }

    @Transactional
    public void updateTask(UUID taskId, TaskUpdate update) {
        if (update == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty_update");
        UUID accountId = accounts.current().accountId();
        Timestamp due = update.dueDate() == null ? null : Timestamp.from(update.dueDate());

        jdbc.update("update replica.academic_task set status = coalesce(?, status), priority = coalesce(?, priority), " +
                "due_date = coalesce(?, due_date), updated_at = clock_timestamp() where id = ? and account_id = ?",
                update.status() == null ? null : update.status().strip().toUpperCase(Locale.ROOT),
                update.priority() == null ? null : update.priority().strip().toUpperCase(Locale.ROOT),
                due, taskId, accountId);
    }

    @Transactional
    public void deleteTask(UUID taskId) {
        UUID accountId = accounts.current().accountId();
        jdbc.update("delete from replica.academic_task where id = ? and account_id = ?", taskId, accountId);
    }

    private static TaskItem mapTask(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaskItem(
                rs.getObject("id", UUID.class),
                rs.getObject("account_id", UUID.class),
                rs.getObject("community_id", UUID.class),
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
