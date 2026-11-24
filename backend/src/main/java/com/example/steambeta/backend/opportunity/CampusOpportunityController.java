package com.example.steambeta.backend.opportunity;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/opportunities")
public class CampusOpportunityController {
    private final CampusOpportunityService opportunityService;

    public CampusOpportunityController(CampusOpportunityService opportunityService) {
        this.opportunityService = opportunityService;
    }

    @GetMapping
    public List<CampusOpportunityService.OpportunityItem> list(@RequestParam(required = false) String category) {
        return opportunityService.list(category);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampusOpportunityService.OpportunityItem create(@RequestBody CampusOpportunityService.OpportunityDraft draft) {
        return opportunityService.create(draft);
    }

    @PatchMapping("/{opportunityId}")
    public CampusOpportunityService.OpportunityItem update(@PathVariable UUID opportunityId,
                                                           @RequestBody CampusOpportunityService.OpportunityDraft draft) {
        return opportunityService.update(opportunityId, draft);
    }

    @DeleteMapping("/{opportunityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID opportunityId) {
        opportunityService.delete(opportunityId);
    }
}
