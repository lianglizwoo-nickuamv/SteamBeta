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

    public record PublicStudyDetail(
            UUID accountId,
            String alias,
            String declaredUniversity,
            String declaredCareer,
            List<String> subjects,
            List<String> interests,
            String canHelpWith,
            String needsHelpWith,
            String availability,
            String bio,
            boolean contactEmailEnabled,
            String publicEmail,
            String avatarUrl
    ) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public StudyTeamService(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    public StudyProfile myProfile() {
        UUID accountId = accounts.current().accountId();
        var rows = jdbc.query(
                "select * from replica.read_study_profile_v15(?::uuid)",
                (rs, row) -> mapProfile(rs),
                accountId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el perfil de la cuenta.");
        }
        return rows.getFirst();
    }

    @Transactional
    public StudyProfile updateProfile(ProfileDraft draft) {
        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datos de ficha de estudio inválidos.");
        }
        UUID accountId = accounts.current().accountId();
        String[] subjs = draft.subjects() == null ? new String[0] : draft.subjects().toArray(new String[0]);
        String[] ints = draft.interests() == null ? new String[0] : draft.interests().toArray(new String[0]);

        return jdbc.query(con -> {
            var ps = con.prepareStatement("select * from replica.upsert_study_profile_v15(?, ?, ?, ?, ?, ?, ?)");
            ps.setObject(1, accountId);
            ps.setArray(2, con.createArrayOf("text", subjs));
            ps.setArray(3, con.createArrayOf("text", ints));
            ps.setString(4, draft.canHelpWith());
            ps.setString(5, draft.needsHelpWith());
            ps.setString(6, draft.availability());
            ps.setBoolean(7, draft.visible());
            return ps;
        }, (rs, row) -> mapProfile(rs)).stream().findFirst().orElseThrow();
    }

    public List<StudyProfile> findMatches(String query) {
        UUID currentId = accounts.current().accountId();
        String cleanQuery = query == null || query.isBlank() ? null : query.strip();
        return jdbc.query(
                "select * from replica.search_study_matches_v15(?::uuid, ?::text)",
                (rs, row) -> mapProfile(rs),
                currentId, cleanQuery
        );
    }

    public PublicStudyDetail publicProfile(UUID targetAccountId) {
        if (targetAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de estudiante inválido.");
        }
        UUID currentId = accounts.current().accountId();
        if (currentId.equals(targetAccountId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes consultar tu propia ficha como compañero.");
        }

        // Verifica mediante las funciones autorizadas V15 que el compañero existe, está activo y tiene su ficha visible.
        // Primero consulta su perfil de estudio directo para obtener su alias y luego valida visibilidad real
        // con search_study_matches_v15(currentId, alias), evitando falsos 404 por el LIMIT 40 de la búsqueda sin filtro.
        StudyProfile direct;
        try {
            var directRows = jdbc.query(
                    "select * from replica.read_study_profile_v15(?::uuid)",
                    (rs, row) -> mapProfile(rs),
                    targetAccountId
            );
            if (directRows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La ficha de estudio no está disponible o es privada.");
            }
            direct = directRows.getFirst();
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La ficha de estudio no está disponible o es privada.");
        }

        if (!direct.visible()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La ficha de estudio no está disponible o es privada.");
        }

        String aliasFilter = (direct.alias() != null && !direct.alias().isBlank()) ? direct.alias().strip() : null;
        StudyProfile match = jdbc.query(
                "select * from replica.search_study_matches_v15(?::uuid, ?::text)",
                (rs, row) -> mapProfile(rs),
                currentId, aliasFilter
        ).stream().filter(p -> targetAccountId.equals(p.accountId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La ficha de estudio no está disponible o es privada."));

        // Consulta autorizada V13 para biografía, avatar_url y correo SOLO si email_public = true.
        // Nunca se expone el correo cuando email_public es false.
        String bio = null;
        boolean emailEnabled = false;
        String publicEmail = null;
        String avatarUrl = null;

        var rows = jdbc.query(
                "select bio, email_public, email, avatar_url from replica.read_full_account_profile(?::uuid)",
                (rs, row) -> {
                    boolean isPublic = rs.getBoolean("email_public");
                    String email = rs.getString("email");
                    boolean canContact = isPublic && email != null && !email.isBlank();
                    return new Object[]{rs.getString("bio"), canContact, canContact ? email.strip() : null, rs.getString("avatar_url")};
                },
                targetAccountId
        );
        if (!rows.isEmpty()) {
            Object[] info = rows.getFirst();
            bio = (String) info[0];
            emailEnabled = (Boolean) info[1];
            publicEmail = (String) info[2];
            avatarUrl = (String) info[3];
        }

        return new PublicStudyDetail(
                match.accountId(),
                match.alias(),
                match.declaredUniversity(),
                match.declaredCareer(),
                match.subjects(),
                match.interests(),
                match.canHelpWith(),
                match.needsHelpWith(),
                match.availability(),
                bio,
                emailEnabled,
                publicEmail,
                avatarUrl
        );
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
