package com.example.steambeta.backend.community;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommunityController {
    private final CommunityService community;

    public CommunityController(CommunityService community) {
        this.community = community;
    }

    @GetMapping("/api/me/communities")
    public List<CommunityService.Community> mine() {
        return community.mine();
    }

    @GetMapping("/api/communities/{communityId}/posts")
    public CommunityService.Page posts(@PathVariable UUID communityId,
                                      @RequestParam(required = false) Instant beforeTime,
                                      @RequestParam(required = false) UUID beforeId,
                                      @RequestParam(defaultValue = "20") int limit) {
        return community.posts(communityId, beforeTime, beforeId, limit);
    }

    @PostMapping("/api/communities/{communityId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.Post create(@PathVariable UUID communityId,
                                        @RequestBody CommunityService.Draft draft) {
        return community.create(communityId, draft);
    }
}
