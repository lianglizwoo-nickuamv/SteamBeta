package com.example.steambeta.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.Created register(@RequestBody AccountService.Registration registration) {
        return accounts.register(registration);
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
}
