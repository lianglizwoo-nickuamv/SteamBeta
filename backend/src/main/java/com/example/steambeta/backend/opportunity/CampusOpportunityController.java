package com.example.steambeta.backend.opportunity;

import java.util.List;
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
}
