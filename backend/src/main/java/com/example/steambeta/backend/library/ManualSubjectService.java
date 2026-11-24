package com.example.steambeta.backend.library;

import com.example.steambeta.backend.auth.AccountService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ManualSubjectService {

    public record ManualSubject(
            UUID id,
            UUID accountId,
            String name,
            String code,
            int academicYear,
            String termLabel,
            String groupSection,
            String teacherName,
            Integer credits,
            String schedule,
            String notes,
            String source,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ManualSubjectDraft(
            String name,
            String code,
            Integer academicYear,
            String termLabel,
            String groupSection,
            String teacherName,
            Integer credits,
            String schedule,
            String notes
    ) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public ManualSubjectService(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    public List<ManualSubject> list(Integer academicYear, String termLabel) {
        UUID actor = accounts.current().accountId();
        String cleanTerm = termLabel == null || termLabel.isBlank() ? null : termLabel.strip();
        return jdbc.query(
                "select * from replica.list_student_manual_subjects_v16(?::uuid, ?::integer, ?::text)",
                (rs, row) -> map(rs),
                actor, academicYear, cleanTerm
        );
    }

    @Transactional
    public ManualSubject create(ManualSubjectDraft draft) {
        validateDraft(draft);
        UUID actor = accounts.current().accountId();
        return jdbc.queryForObject(
                "select * from replica.create_student_manual_subject_v16(?::uuid, ?::text, ?::text, ?::integer, ?::text, ?::text, ?::text, ?::integer, ?::text, ?::text)",
                (rs, row) -> map(rs),
                actor,
                draft.name().strip(),
                cleanOptional(draft.code()),
                draft.academicYear(),
                draft.termLabel().strip(),
                cleanOptional(draft.groupSection()),
                cleanOptional(draft.teacherName()),
                draft.credits(),
                cleanOptional(draft.schedule()),
                cleanOptional(draft.notes())
        );
    }

    @Transactional
    public ManualSubject update(UUID subjectId, ManualSubjectDraft draft) {
        if (subjectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de materia inválido.");
        }
        validateDraft(draft);
        UUID actor = accounts.current().accountId();
        return jdbc.queryForObject(
                "select * from replica.update_student_manual_subject_v16(?::uuid, ?::uuid, ?::text, ?::text, ?::integer, ?::text, ?::text, ?::text, ?::integer, ?::text, ?::text)",
                (rs, row) -> map(rs),
                actor,
                subjectId,
                draft.name().strip(),
                cleanOptional(draft.code()),
                draft.academicYear(),
                draft.termLabel().strip(),
                cleanOptional(draft.groupSection()),
                cleanOptional(draft.teacherName()),
                draft.credits(),
                cleanOptional(draft.schedule()),
                cleanOptional(draft.notes())
        );
    }

    @Transactional
    public void delete(UUID subjectId) {
        if (subjectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de materia inválido.");
        }
        UUID actor = accounts.current().accountId();
        jdbc.query("select replica.delete_student_manual_subject_v16(?::uuid, ?::uuid)", rs -> {}, actor, subjectId);
    }

    private static void validateDraft(ManualSubjectDraft draft) {
        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datos de la materia inválidos.");
        }
        String name = draft.name() == null ? "" : draft.name().strip();
        if (name.length() < 2 || name.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de la materia debe tener entre 2 y 120 caracteres.");
        }
        if (draft.academicYear() == null || draft.academicYear() < 2000 || draft.academicYear() > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El año académico debe estar entre 2000 y 2100.");
        }
        String term = draft.termLabel() == null ? "" : draft.termLabel().strip();
        if (term.isEmpty() || term.length() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El período académico o semestre es obligatorio (máx. 40 caracteres).");
        }
        if (draft.code() != null && draft.code().strip().length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código de la materia no puede superar 32 caracteres.");
        }
        if (draft.groupSection() != null && draft.groupSection().strip().length() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El grupo o sección no puede superar 40 caracteres.");
        }
        if (draft.teacherName() != null && draft.teacherName().strip().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre del docente no puede superar 120 caracteres.");
        }
        if (draft.credits() != null && (draft.credits() < 0 || draft.credits() > 40)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los créditos deben estar entre 0 y 40.");
        }
        if (draft.schedule() != null && draft.schedule().strip().length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El horario no puede superar 160 caracteres.");
        }
        if (draft.notes() != null && draft.notes().strip().length() > 1500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La descripción o notas personales no pueden superar 1500 caracteres.");
        }
    }

    private static String cleanOptional(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static ManualSubject map(ResultSet rs) throws SQLException {
        int rawCredits = rs.getInt("credits");
        Integer credits = rs.wasNull() ? null : rawCredits;
        var createdTs = rs.getTimestamp("created_at");
        var updatedTs = rs.getTimestamp("updated_at");
        return new ManualSubject(
                rs.getObject("id", UUID.class),
                rs.getObject("account_id", UUID.class),
                rs.getString("name"),
                rs.getString("code"),
                rs.getInt("academic_year"),
                rs.getString("term_label"),
                rs.getString("group_section"),
                rs.getString("teacher_name"),
                credits,
                rs.getString("schedule"),
                rs.getString("notes"),
                "MANUAL",
                createdTs == null ? null : createdTs.toInstant(),
                updatedTs == null ? null : updatedTs.toInstant()
        );
    }
}
