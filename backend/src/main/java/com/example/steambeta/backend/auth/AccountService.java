package com.example.steambeta.backend.auth;

import com.example.steambeta.backend.library.LibraryDtos;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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

    // Registro rediseñado: Solicita alias, login, correo y contraseña con confirmación obligatoria.
    public record Registration(String alias, String login, String email, String password,
                               String confirmPassword, String declaredUniversity, String declaredCareer) {
        public Registration(String alias, String login, String email, String password, String confirmPassword) {
            this(alias, login, email, password, confirmPassword, null, null);
        }
    }

    public record Created(UUID accountId) {}

    public record Profile(UUID accountId, String alias, String declaredUniversity,
                          String declaredCareer, boolean institutionalIdentityVerified) {}

    public record FullProfile(UUID accountId, String login, String email, boolean emailVerified,
                              boolean emailPublic, String alias, String declaredUniversity,
                              String declaredCareer, Integer declaredSemester, String bio, String avatarUrl,
                              String academicInterests, String globalRole, boolean institutionalIdentityVerified,
                              int communitiesCount, int postsCount) {}

    public record ProfileUpdate(String alias, String declaredUniversity, String declaredCareer,
                                Integer declaredSemester, String bio, String avatarUrl,
                                String academicInterests, Boolean emailPublic) {}

    public record Sanction(UUID id, String action, String reason, String evidenceContext,
                           Instant expiresAt, Instant createdAt, boolean active,
                           String appealStatus, String appealNote) {}

    public record AppealRequest(String note) {}

    private record ProfileRow(Profile profile, UUID studentId, String academicUniversityName) {}

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final EmailVerificationService emailService;
    private final AvatarStorageService avatarStorage;

    public AccountService(JdbcTemplate jdbc, PasswordEncoder passwords,
                          EmailVerificationService emailService, AvatarStorageService avatarStorage) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.emailService = emailService;
        this.avatarStorage = avatarStorage;
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
        if (request == null) throw invalidRegistration();

        // 1. Alias público (requerido: 2 a 60 caracteres)
        String alias = text(request.alias(), 2, 60);

        // 2. Identificador de acceso único (requerido: 3 a 80 caracteres en minúsculas)
        if (request.login() == null || request.login().isBlank()) throw invalidRegistration();
        String login = request.login().strip().toLowerCase(Locale.ROOT);
        if (!login.matches("[a-z0-9][a-z0-9._@-]{2,79}")) {
            throw invalidRegistration();
        }

        // 3. Correo electrónico obligatorio (requerido: formato RFC 5322 válido)
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email_required");
        }
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (!email.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_email_format");
        }

        // 4. Contraseña y Confirmación (ambos obligatorios; mínimo 12 caracteres, máximo 72 bytes UTF-8)
        if (request.password() == null) throw invalidRegistration();
        int passwordBytes = request.password().getBytes(StandardCharsets.UTF_8).length;
        if (request.password().codePointCount(0, request.password().length()) < 12 || passwordBytes > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password_requirements_unmet");
        }
        if (request.confirmPassword() == null || request.confirmPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "confirm_password_required");
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "passwords_do_not_match");
        }

        UUID accountId;
        String passwordHash = passwords.encode(request.password());
        try {
            // Inserción atómica y segura mediante la función de registro V13
            accountId = jdbc.queryForObject(
                    "select replica.register_account(?, ?, ?, ?)", UUID.class,
                    alias, login, email, passwordHash);
        } catch (DuplicateKeyException duplicate) {
            String msg = duplicate.getMessage() != null ? duplicate.getMessage().toLowerCase(Locale.ROOT) : "";
            if (msg.contains("email") || msg.contains("duplicate_email")
                    || msg.contains("access_account_lower_email_uidx") || msg.contains("access_account_email_key")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "email_unavailable");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "login_unavailable");
        } catch (org.springframework.dao.DataAccessException dae) {
            String msg = dae.getMessage() != null ? dae.getMessage().toLowerCase(Locale.ROOT) : "";
            if (msg.contains("23505") || msg.contains("duplicate_email") || msg.contains("email_key")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "email_unavailable");
            } else if (msg.contains("duplicate_login") || msg.contains("login_identifier_key")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "login_unavailable");
            }
            // No se capturan silenciosamente errores de BD ni se permite registrar sin almacenar el correo.
            // Si la función o columnas no existen en la BD local, se notifica claramente la necesidad de migración.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "database_schema_migration_required: la base steam_academico requiere la migración incremental V13", dae);
        }

        // Si el proveedor SMTP externo está configurado, generar token de verificación
        if (accountId != null) {
            emailService.createVerificationTokenIfConfigured(accountId, email);
        }

        return new Created(accountId);
    }

    public Profile me() {
        return profile().profile();
    }

    public FullProfile fullProfile() {
        UUID id = current().accountId();
        String role = current().globalRole();
        String login = current().getUsername();

        // Lectura autorizada mediante la función SECURITY DEFINER replica.read_full_account_profile(?).
        // Respeta el principio de Row Level Security (RLS) sin requerir permisos directos sobre tablas.
        try {
            var rows = jdbc.query(
                    "select * from replica.read_full_account_profile(?)",
                    (rs, row) -> new FullProfile(
                            rs.getObject("account_id", UUID.class),
                            rs.getString("login_identifier"),
                            rs.getString("email"),
                            rs.getBoolean("email_verified"),
                            rs.getBoolean("email_public"),
                            rs.getString("alias"),
                            rs.getString("declared_university"),
                            rs.getString("declared_career"),
                            rs.getObject("declared_semester", Integer.class),
                            rs.getString("bio"),
                            rs.getString("avatar_url"),
                            rs.getString("academic_interests"),
                            rs.getString("global_role"),
                            false,
                            rs.getInt("communities_count"),
                            rs.getInt("posts_count")
                    ), id);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        } catch (Exception ex) {
            // Compatibilidad defensiva con esquema previo si la función V13 no está aplicada
        }

        var base = profile().profile();
        return new FullProfile(base.accountId(), login, null, false, false,
                base.alias(), base.declaredUniversity(), base.declaredCareer(), null,
                null, null, null, role, false, 0, 0);
    }

    @Transactional
    public FullProfile updateProfile(ProfileUpdate update) {
        if (update == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_update");
        UUID id = current().accountId();
        String alias = text(update.alias(), 2, 60);
        String university = optionalText(update.declaredUniversity());
        String career = optionalText(update.declaredCareer());
        Integer semester = update.declaredSemester();
        if (semester != null && (semester < 1 || semester > 20)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_semester");
        }
        String bio = update.bio() == null || update.bio().isBlank() ? null : text(update.bio(), 1, 500);
        // Preservar siempre la referencia administrada de avatar_url existente en servidor;
        // los cambios de fotografía se gestionan exclusivamente vía /api/account/avatar.
        FullProfile existing = fullProfile();
        String avatar = existing.avatarUrl();
        String interests = update.academicInterests() == null || update.academicInterests().isBlank() ? null : text(update.academicInterests(), 1, 300);
        boolean emailPublic = Boolean.TRUE.equals(update.emailPublic());

        // Actualización autorizada exclusivamente mediante función SECURITY DEFINER V17
        // usando el identificador autenticado del contexto de seguridad (respeta RLS).
        jdbc.query(
                "select * from replica.update_account_profile_v2(?::uuid, ?::text, ?::text, ?::text, ?::integer, ?::text, ?::text, ?::text, ?::boolean)",
                (rs, row) -> null,
                id, alias, university, career, semester, bio, avatar, interests, emailPublic);
        return fullProfile();
    }

    @Transactional
    public FullProfile uploadAvatar(org.springframework.web.multipart.MultipartFile file) {
        UUID id = current().accountId();
        FullProfile existing = fullProfile();
        String previousAvatarUrl = existing.avatarUrl();

        AvatarStorageService.StoredAvatar stored = avatarStorage.store(file);
        try {
            jdbc.query(
                    "select * from replica.update_account_profile_v2(?::uuid, ?::text, ?::text, ?::text, ?::integer, ?::text, ?::text, ?::text, ?::boolean)",
                    (rs, row) -> null,
                    id,
                    existing.alias(),
                    existing.declaredUniversity(),
                    existing.declaredCareer(),
                    existing.declaredSemester(),
                    existing.bio(),
                    stored.avatarUrl(),
                    existing.academicInterests(),
                    existing.emailPublic()
            );
        } catch (RuntimeException ex) {
            // Evitar archivos huérfanos si falla PostgreSQL
            avatarStorage.deleteQuietly(stored.storageKey());
            throw ex;
        }

        // Eliminar fotografía anterior de forma segura solo tras persistir la nueva
        if (previousAvatarUrl != null && !previousAvatarUrl.equals(stored.avatarUrl())) {
            avatarStorage.deleteByAvatarUrlQuietly(previousAvatarUrl);
        }
        return fullProfile();
    }

    @Transactional
    public FullProfile deleteAvatar() {
        UUID id = current().accountId();
        FullProfile existing = fullProfile();
        String previousAvatarUrl = existing.avatarUrl();

        jdbc.query(
                "select * from replica.update_account_profile_v2(?::uuid, ?::text, ?::text, ?::text, ?::integer, ?::text, ?::text, ?::text, ?::boolean)",
                (rs, row) -> null,
                id,
                existing.alias(),
                existing.declaredUniversity(),
                existing.declaredCareer(),
                existing.declaredSemester(),
                existing.bio(),
                null,
                existing.academicInterests(),
                existing.emailPublic()
        );

        if (previousAvatarUrl != null && !previousAvatarUrl.isBlank()) {
            avatarStorage.deleteByAvatarUrlQuietly(previousAvatarUrl);
        }
        return fullProfile();
    }

    public record AvatarDownload(org.springframework.core.io.Resource resource, String mimeType) {}

    public AvatarDownload readAvatarForAccount(UUID targetAccountId) {
        // Requiere sesión autenticada
        current();
        if (targetAccountId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fotografía no disponible.");
        }
        var rows = jdbc.query(
                "select avatar_url from replica.read_full_account_profile(?::uuid)",
                (rs, row) -> rs.getString("avatar_url"),
                targetAccountId
        );
        if (rows.isEmpty() || rows.getFirst() == null || rows.getFirst().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fotografía no disponible.");
        }
        String avatarUrl = rows.getFirst();
        return new AvatarDownload(avatarStorage.loadByAvatarUrl(avatarUrl), avatarStorage.mimeTypeForAvatarUrl(avatarUrl));
    }

    public List<Sanction> mySanctions() {
        UUID id = current().accountId();
        try {
            return jdbc.query("select * from replica.account_sanction where target_account_id = ? order by created_at desc",
                    (rs, row) -> new Sanction(
                            rs.getObject("id", UUID.class),
                            rs.getString("action"),
                            rs.getString("reason"),
                            rs.getString("evidence_context"),
                            rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getBoolean("active"),
                            rs.getString("appeal_status"),
                            rs.getString("appeal_note")
                    ), id);
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Transactional
    public void appealSanction(UUID sanctionId, AppealRequest request) {
        if (request == null || request.note() == null || request.note().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appeal_note_required");
        }
        UUID id = current().accountId();
        int updated = jdbc.update("update replica.account_sanction set appeal_status = 'REQUESTED', appeal_note = ? " +
                        "where id = ? and target_account_id = ? and appeal_status = 'NONE'",
                text(request.note(), 5, 2000), sanctionId, id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "sanction_not_eligible_for_appeal");
        }
    }

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

