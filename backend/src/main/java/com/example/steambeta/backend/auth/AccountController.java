package com.example.steambeta.backend.auth;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController {
    private final AccountService accounts;
    private final EmailVerificationService emailService;

    public AccountController(AccountService accounts, EmailVerificationService emailService) {
        this.accounts = accounts;
        this.emailService = emailService;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.Created register(@RequestBody AccountService.Registration registration) {
        return accounts.register(registration);
    }

    @GetMapping("/api/auth/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestParam("token") String token) {
        boolean verified = emailService.verifyToken(token);
        if (verified) {
            return ResponseEntity.ok(Map.of(
                    "status", "VERIFIED",
                    "message", "¡Correo institucional verificado exitosamente! Ya puedes iniciar sesión con tu correo."
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "INVALID_OR_EXPIRED",
                    "message", "El enlace de verificación es inválido, ya fue utilizado o ha expirado (24 horas)."
            ));
        }
    }

    @GetMapping("/api/auth/email-status")
    public Map<String, Object> emailStatus() {
        return Map.of(
                "mailProviderConfigured", emailService.isMailConfigured(),
                "providerNotice", emailService.isMailConfigured()
                        ? "Proveedor SMTP institucional configurado."
                        : "Servicio de correo pendiente de configuración externa (STEAMBETA_MAIL_HOST no definido)."
        );
    }

    @GetMapping("/api/account")
    public AccountService.Profile me() {
        return accounts.me();
    }

    @GetMapping("/api/account/full")
    public AccountService.FullProfile fullProfile() {
        return accounts.fullProfile();
    }

    @PatchMapping("/api/account")
    public AccountService.FullProfile updateProfile(@RequestBody AccountService.ProfileUpdate update) {
        return accounts.updateProfile(update);
    }

    @GetMapping("/api/account/sanctions")
    public List<AccountService.Sanction> mySanctions() {
        return accounts.mySanctions();
    }

    @PostMapping("/api/account/sanctions/{sanctionId}/appeal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void appealSanction(@PathVariable UUID sanctionId, @RequestBody AccountService.AppealRequest request) {
        accounts.appealSanction(sanctionId, request);
    }
}
