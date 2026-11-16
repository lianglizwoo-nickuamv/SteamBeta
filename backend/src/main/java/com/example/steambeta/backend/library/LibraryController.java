package com.example.steambeta.backend.library;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import com.example.steambeta.backend.auth.AccountService;

@RestController
public class LibraryController {
    private final LibraryService library;
    private final AccountService accounts;
    public LibraryController(LibraryService library, AccountService accounts) {
        this.library=library;
        this.accounts=accounts;
    }
    @GetMapping("/api/auth/csrf") public LibraryDtos.Csrf csrf(CsrfToken token) {
        return new LibraryDtos.Csrf(token.getHeaderName(),token.getToken());
    }
    @GetMapping("/api/me") public LibraryDtos.Identity me() { return accounts.sessionIdentity(); }
    @GetMapping("/api/me/library") public List<LibraryDtos.SubjectSummary> subjects() { return library.library(); }
    @GetMapping("/api/me/library/{offeringId}") public LibraryDtos.Detail detail(@PathVariable UUID offeringId) {
        return library.detail(offeringId);
    }
}
