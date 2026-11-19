package com.example.steambeta.backend.auth;

import com.example.steambeta.backend.library.LibraryDtos;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    public record Registration(String login, String password, String alias,
                               String declaredUniversity, String declaredCareer) {}
    public record Created(UUID accountId) {}
    public record Profile(UUID accountId, String alias, String declaredUniversity,
                          String declaredCareer, boolean institutionalIdentityVerified) {}
    public record FullProfile(UUID accountId, String alias, String declaredUniversity,
                              String declaredCareer, String bio, String avatarUrl,
                              String academicInterests, boolean institutionalIdentityVerified) {}
    public record ProfileUpdate(String alias, String declaredUniversity, String declaredCareer,
                                String bio, String avatarUrl, String academicInterests) {}
    private record ProfileRow(Profile profile, UUID studentId, String academicUniversityName) {}

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    public AccountService(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    public AccountPrincipal current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Session required");
        }
        return principal;
    }

    @Transactional
    public Created register(Registration request) {
        if (request == null || request.login() == null || request.password() == null) {
            throw invalidRegistration();
        }
        String login = request.login().strip().toLowerCase(Locale.ROOT);
        int passwordBytes = request.password().getBytes(StandardCharsets.UTF_8).length;
        if (!login.matches("[a-z0-9][a-z0-9._@-]{2,79}")
                || request.password().codePointCount(0, request.password().length()) < 12
                || passwordBytes > 72) {
            throw invalidRegistration();
        }
        String alias = text(request.alias(), 2, 60);
        String university = optionalText(request.declaredUniversity());
        String career = optionalText(request.declaredCareer());
        try {
            UUID accountId = jdbc.queryForObject(
                    "select replica.register_account(?, ?, ?, ?, ?)", UUID.class,
                    login, passwords.encode(request.password()), alias, university, career);
            return new Created(accountId);
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "login_unavailable");
        }
    }

    public Profile me() {
        return profile().profile();
    }

    public FullProfile fullProfile() {
        UUID id = current().accountId();
        try {
            var rows = jdbc.query(
                    "select ap.account_id, ap.alias, ap.declared_university, ap.declared_career, " +
                    "ap.bio, ap.avatar_url, ap.academic_interests " +
                    "from replica.account_profile ap where ap.account_id = ?",
                    (rs, row) -> new FullProfile(
                            rs.getObject("account_id", UUID.class),
                            rs.getString("alias"),
                            rs.getString("declared_university"),
                            rs.getString("declared_career"),
                            rs.getString("bio"),
                            rs.getString("avatar_url"),
                            rs.getString("academic_interests"),
                            false
                    ), id);
            if (!rows.isEmpty()) return rows.getFirst();
        } catch (Exception ex) {
            // Compatibilidad defensiva si las columnas de V6 aún no se han aplicado en la BD local
        }
        var base = profile().profile();
        return new FullProfile(base.accountId(), base.alias(), base.declaredUniversity(),
                base.declaredCareer(), null, null, null, false);
    }

    @Transactional
    public FullProfile updateProfile(ProfileUpdate update) {
        if (update == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_update");
        UUID id = current().accountId();
        String alias = text(update.alias(), 2, 60);
        String university = optionalText(update.declaredUniversity());
        String career = optionalText(update.declaredCareer());
        String bio = update.bio() == null || update.bio().isBlank() ? null : text(update.bio(), 1, 500);
        String avatar = update.avatarUrl() == null || update.avatarUrl().isBlank() ? null : text(update.avatarUrl(), 1, 255);
        String interests = update.academicInterests() == null || update.academicInterests().isBlank() ? null : text(update.academicInterests(), 1, 300);

        try {
            return jdbc.queryForObject(
                    "select * from replica.update_account_profile(?, ?, ?, ?, ?, ?, ?)",
                    (rs, row) -> new FullProfile(
                            rs.getObject("account_id", UUID.class),
                            rs.getString("alias"),
                            rs.getString("declared_university"),
                            rs.getString("declared_career"),
                            rs.getString("bio"),
                            rs.getString("avatar_url"),
                            rs.getString("academic_interests"),
                            false
                    ), id, alias, university, career, bio, avatar, interests);
        } catch (Exception ex) {
            // Fallback si la función update_account_profile aún no está migrada: actualizar lo disponible
            jdbc.update("update replica.account_profile set alias = ?, declared_university = ?, declared_career = ? where account_id = ?",
                    alias, university, career, id);
            return new FullProfile(id, alias, university, career, bio, avatar, interests, false);
        }
    }

    // Keep the original login and /api/me response usable by the academic client.
    public LibraryDtos.Identity sessionIdentity() {
        var row = profile();
        String university = row.academicUniversityName() != null
                ? row.academicUniversityName() : "Datos institucionales sin verificar";
        return new LibraryDtos.Identity(row.studentId(), row.profile().alias(), university);
    }

    private ProfileRow profile() {
        UUID id = current().accountId();
        var profiles = jdbc.query("select * from replica.read_account_profile(?)", (rs, row) ->
                new ProfileRow(new Profile(rs.getObject("account_id", UUID.class), rs.getString("alias"),
                        rs.getString("declared_university"), rs.getString("declared_career"), false),
                        rs.getObject("student_id", UUID.class), rs.getString("academic_university_name")), id);
        return profiles.stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : text(value, 1, 120);
    }

    private static String text(String value, int minimum, int maximum) {
        if (value == null) {
            throw invalidRegistration();
        }
        String result = value.strip();
        int length = result.codePointCount(0, result.length());
        if (length < minimum || length > maximum) {
            throw invalidRegistration();
        }
        return result;
    }

    private static ResponseStatusException invalidRegistration() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_registration");
    }
}
