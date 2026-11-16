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

    @PatchMapping("/api/communities/{communityId}/posts/{postId}")
    public CommunityService.Post editPost(@PathVariable UUID communityId, @PathVariable UUID postId,
                                           @RequestBody CommunityService.PostEdit edit) {
        return community.editPost(communityId, postId, edit);
    }

    @DeleteMapping("/api/communities/{communityId}/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable UUID communityId, @PathVariable UUID postId) { community.deletePost(communityId, postId); }

    @GetMapping("/api/communities/{communityId}/posts/{postId}/comments")
    public List<CommunityService.Comment> comments(@PathVariable UUID communityId, @PathVariable UUID postId) {
        return community.comments(communityId, postId);
    }

    @PostMapping("/api/communities/{communityId}/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.Comment createComment(@PathVariable UUID communityId, @PathVariable UUID postId,
                                                    @RequestBody CommunityService.CommentDraft draft) {
        return community.createComment(communityId, postId, draft);
    }

    @PatchMapping("/api/communities/{communityId}/comments/{commentId}")
    public CommunityService.Comment editComment(@PathVariable UUID communityId, @PathVariable UUID commentId,
                                                  @RequestBody CommunityService.CommentDraft draft) {
        return community.editComment(communityId, commentId, draft);
    }

    @DeleteMapping("/api/communities/{communityId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID communityId, @PathVariable UUID commentId) { community.deleteComment(communityId, commentId); }

    @PostMapping("/api/communities/{communityId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public java.util.UUID report(@PathVariable UUID communityId, @RequestBody CommunityService.ReportDraft draft) {
        return community.report(communityId, draft);
    }

    @GetMapping("/api/communities/{communityId}/moderation/reports")
    public List<CommunityService.Report> reports(@PathVariable UUID communityId) { return community.reports(communityId); }

    @PostMapping("/api/communities/{communityId}/moderation/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@PathVariable UUID communityId, @RequestBody CommunityService.Moderation request) {
        community.hide(communityId, request);
    }

    @PostMapping("/api/communities/{communityId}/moderation/reports/{reportId}/resolution")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable UUID communityId, @PathVariable UUID reportId,
                        @RequestBody CommunityService.Resolution resolution) {
        community.resolve(communityId, reportId, resolution);
    }

    @PostMapping("/api/communities/{communityId}/moderation/reports/{reportId}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hideReported(@PathVariable UUID communityId, @PathVariable UUID reportId,
                             @RequestBody CommunityService.Resolution resolution) {
        community.hideReported(communityId, reportId, resolution);
    }
}
