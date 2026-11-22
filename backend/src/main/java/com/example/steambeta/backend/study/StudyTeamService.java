package com.example.steambeta.backend.study;

import com.example.steambeta.backend.auth.AccountService;
import java.sql.Array;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyTeamService {
    public record StudyProfile(
            UUID accountId,
            String alias,
            String declaredUniversity,
            String declaredCareer,
            List<String> subjects,
            List<String> interests,
            String canHelpWith,
            String needsHelpWith,
            String availability,
            boolean visible
    ) {}

    public record ProfileDraft(
            List<String> subjects,
            List<String> interests,
            String canHelpWith,
            String needsHelpWith,
            String availability,
            boolean visible
    ) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public StudyTeamService(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    public StudyProfile myProfile() {
        UUID accountId = accounts.current().accountId();
        try {
            var rows = jdbc.query(
                    "select p.account_id, a.alias, a.declared_university, a.declared_career, " +
                    "p.subjects, p.interests, p.can_help_with, p.needs_help_with, p.availability, p.visible " +
                    "from replica.study_profile p " +
                    "join replica.account_profile a on a.account_id = p.account_id " +
                    "where p.account_id = ?",
                    (rs, row) -> mapProfile(rs), accountId);

            if (!rows.isEmpty()) return rows.getFirst();
        } catch (Exception ignored) {}

        var currentAcc = accounts.fullProfile();
        return new StudyProfile(accountId, currentAcc.alias(), currentAcc.declaredUniversity(),
                currentAcc.declaredCareer(), List.of(), List.of(), null, null, null, true);
    }

    @Transactional
    public StudyProfile updateProfile(ProfileDraft draft) {
        if (draft == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_draft");
        UUID accountId = accounts.current().accountId();

        String[] subjs = draft.subjects() == null ? new String[0] : draft.subjects().toArray(new String[0]);
        String[] ints = draft.interests() == null ? new String[0] : draft.interests().toArray(new String[0]);

        try {
            jdbc.update("insert into replica.study_profile(account_id, subjects, interests, can_help_with, needs_help_with, availability, visible, updated_at) " +
                    "values (?, ?, ?, ?, ?, ?, ?, clock_timestamp()) " +
                    "on conflict (account_id) do update set " +
                    "subjects = excluded.subjects, interests = excluded.interests, " +
                    "can_help_with = excluded.can_help_with, needs_help_with = excluded.needs_help_with, " +
                    "availability = excluded.availability, visible = excluded.visible, updated_at = clock_timestamp()",
                    accountId, subjs, ints, draft.canHelpWith(), draft.needsHelpWith(), draft.availability(), draft.visible());

            return myProfile();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "could_not_save_study_profile");
        }
    }

    public List<StudyProfile> findMatches(String subjectFilter) {
        UUID currentId = accounts.current().accountId();
        String sql = "select p.account_id, a.alias, a.declared_university, a.declared_career, " +
                "p.subjects, p.interests, p.can_help_with, p.needs_help_with, p.availability, p.visible " +
                "from replica.study_profile p " +
                "join replica.account_profile a on a.account_id = p.account_id " +
                "where p.account_id <> ? and p.visible " +
                "order by p.updated_at desc limit 30";
        try {
            return jdbc.query(sql, (rs, row) -> mapProfile(rs), currentId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static StudyProfile mapProfile(java.sql.ResultSet rs) throws java.sql.SQLException {
        Array subArr = rs.getArray("subjects");
        List<String> subList = subArr == null ? List.of() : Arrays.asList((String[]) subArr.getArray());
        Array intArr = rs.getArray("interests");
        List<String> intList = intArr == null ? List.of() : Arrays.asList((String[]) intArr.getArray());

        return new StudyProfile(
                rs.getObject("account_id", UUID.class),
                rs.getString("alias"),
                rs.getString("declared_university"),
                rs.getString("declared_career"),
                subList,
                intList,
                rs.getString("can_help_with"),
                rs.getString("needs_help_with"),
                rs.getString("availability"),
                rs.getBoolean("visible")
        );
    }
}
