package com.example.steambeta.backend.library;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;

@RestController
public class LibraryController {
    private final LibraryService library;
    public LibraryController(LibraryService library) { this.library=library; }
    @GetMapping("/api/auth/csrf") public LibraryDtos.Csrf csrf(CsrfToken token) {
        return new LibraryDtos.Csrf(token.getHeaderName(),token.getToken());
    }
    @GetMapping("/api/me") public LibraryDtos.Identity me() { return library.me(); }
    @GetMapping("/api/me/library") public List<LibraryDtos.SubjectSummary> subjects() { return library.library(); }
    @GetMapping("/api/me/library/{offeringId}") public LibraryDtos.Detail detail(@PathVariable UUID offeringId) {
        return library.detail(offeringId);
    }
}
