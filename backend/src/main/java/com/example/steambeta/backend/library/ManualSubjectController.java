package com.example.steambeta.backend.library;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/library/manual-subjects")
public class ManualSubjectController {

    private final ManualSubjectService service;

    public ManualSubjectController(ManualSubjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<ManualSubjectService.ManualSubject> list(
            @RequestParam(required = false) Integer academicYear,
            @RequestParam(required = false) String termLabel) {
        return service.list(academicYear, termLabel);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManualSubjectService.ManualSubject create(@RequestBody ManualSubjectService.ManualSubjectDraft draft) {
        return service.create(draft);
    }

    @PutMapping("/{subjectId}")
    public ManualSubjectService.ManualSubject update(
            @PathVariable UUID subjectId,
            @RequestBody ManualSubjectService.ManualSubjectDraft draft) {
        return service.update(subjectId, draft);
    }

    @DeleteMapping("/{subjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID subjectId) {
        service.delete(subjectId);
    }
}
