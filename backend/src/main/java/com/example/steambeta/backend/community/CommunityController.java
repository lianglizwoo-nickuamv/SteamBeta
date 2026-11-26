package com.example.steambeta.backend.community;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CommunityController {
    private final CommunityService community;

    public CommunityController(CommunityService community) {
        this.community = community;
    }

    // =========================================================================
    // Operaciones Base de Comunidad y Publicaciones (V4 & V5)
    // =========================================================================

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
    public void deletePost(@PathVariable UUID communityId, @PathVariable UUID postId) {
        community.deletePost(communityId, postId);
    }

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
    public void deleteComment(@PathVariable UUID communityId, @PathVariable UUID commentId) {
        community.deleteComment(communityId, commentId);
    }

    @PostMapping("/api/communities/{communityId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public UUID report(@PathVariable UUID communityId, @RequestBody CommunityService.ReportDraft draft) {
        return community.report(communityId, draft);
    }

    @GetMapping("/api/communities/{communityId}/moderation/reports")
    public List<CommunityService.Report> reports(@PathVariable UUID communityId) {
        return community.reports(communityId);
    }

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

    // =========================================================================
    // FASE 2: Creación de Comunidades, Invitaciones y Gestión de Miembros
    // =========================================================================

    @PostMapping("/api/communities")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.Community createCommunity(@RequestBody CommunityService.CommunityCreate request) {
        return community.createCommunity(request);
    }

    @GetMapping("/api/communities/explore")
    public List<CommunityService.CommunityOverview> exploreCommunities(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category) {
        return community.exploreCommunities(query, category);
    }

    @PostMapping("/api/communities/{communityId}/join-public")
    public CommunityService.Community joinPublicCommunity(@PathVariable UUID communityId) {
        return community.joinPublicCommunity(communityId);
    }

    @PostMapping("/api/communities/{communityId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.Invitation createInvitation(@PathVariable UUID communityId,
                                                         @RequestBody CommunityService.InvitationDraft draft) {
        return community.createInvitation(communityId, draft);
    }

    @PostMapping("/api/communities/join")
    public CommunityService.Community joinByCode(@RequestBody CommunityService.JoinDraft draft) {
        return community.joinByCode(draft);
    }

    @GetMapping("/api/communities/{communityId}/members")
    public List<CommunityService.Member> members(@PathVariable UUID communityId) {
        return community.members(communityId);
    }

    @PutMapping("/api/communities/{communityId}/members/{targetId}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMemberRole(@PathVariable UUID communityId, @PathVariable UUID targetId,
                                 @RequestBody CommunityService.RoleUpdate update) {
        community.updateMemberRole(communityId, targetId, update);
    }

    @DeleteMapping("/api/communities/{communityId}/members/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID communityId, @PathVariable UUID targetId) {
        community.removeMember(communityId, targetId);
    }

    @DeleteMapping("/api/communities/{communityId}/membership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveCommunity(@PathVariable UUID communityId) {
        community.leaveCommunity(communityId);
    }

    // =========================================================================
    // FASE 3: Q&A Académico
    // =========================================================================

    @PostMapping("/api/communities/{communityId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.Question createQuestion(@PathVariable UUID communityId,
                                                    @RequestBody CommunityService.QuestionDraft draft) {
        return community.createQuestion(communityId, draft);
    }

    @GetMapping("/api/communities/{communityId}/questions")
    public List<CommunityService.Question> questions(@PathVariable UUID communityId,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String subject,
                                                     @RequestParam(required = false) String search,
                                                     @RequestParam(defaultValue = "20") int limit) {
        return community.questions(communityId, status, subject, search, limit);
    }

    @PostMapping("/api/communities/{communityId}/posts/{postId}/solution/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptSolution(@PathVariable UUID communityId, @PathVariable UUID postId,
                               @PathVariable UUID commentId) {
        community.acceptSolution(communityId, postId, commentId);
    }

    // =========================================================================
    // FASE 4: Recursos Académicos
    // =========================================================================

    @PostMapping("/api/communities/{communityId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.ResourceItem publishResource(@PathVariable UUID communityId,
                                                         @RequestBody CommunityService.ResourceDraft draft) {
        return community.publishResource(communityId, draft);
    }

    @PostMapping(value = "/api/communities/{communityId}/resources/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.ResourceItem publishResourceWithFile(
            @PathVariable UUID communityId,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("resourceType") String resourceType,
            @RequestParam(value = "subjectName", required = false) String subjectName,
            @RequestPart("file") MultipartFile file) {
        return community.publishResourceWithFile(communityId, title, description, resourceType, subjectName, file);
    }

    @GetMapping("/api/communities/{communityId}/resources/{resourceId}/download")
    public ResponseEntity<Resource> downloadResource(@PathVariable UUID communityId,
                                                     @PathVariable UUID resourceId) {
        var dl = community.downloadResource(communityId, resourceId);
        ContentDisposition disp = ContentDisposition.attachment()
                .filename(dl.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(dl.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disp.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(dl.resource());
    }

    @GetMapping("/api/communities/{communityId}/resources")
    public List<CommunityService.ResourceItem> resources(@PathVariable UUID communityId,
                                                         @RequestParam(required = false) String type,
                                                         @RequestParam(required = false) String subject,
                                                         @RequestParam(required = false) String search) {
        return community.resources(communityId, type, subject, search);
    }

    // =========================================================================
    // FASE 5: Calendario y Actividades
    // =========================================================================

    @PostMapping("/api/communities/{communityId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.EventItem createEvent(@PathVariable UUID communityId,
                                                   @RequestBody CommunityService.EventDraft draft) {
        return community.createEvent(communityId, draft);
    }

    @GetMapping("/api/communities/{communityId}/events")
    public List<CommunityService.EventItem> events(@PathVariable UUID communityId,
                                                   @RequestParam(required = false) Instant from,
                                                   @RequestParam(required = false) Instant to) {
        return community.events(communityId, from, to);
    }

    @GetMapping("/api/me/events/upcoming")
    public List<CommunityService.UpcomingEvent> myUpcomingEvents(@RequestParam(defaultValue = "10") int limit) {
        return community.myUpcomingEvents(limit);
    }

    // =========================================================================
    // FASE 6 & 7: Notificaciones y Estadísticas
    // =========================================================================

    @GetMapping("/api/me/notifications")
    public List<CommunityService.NotificationItem> myNotifications(@RequestParam(defaultValue = "20") int limit) {
        return community.myNotifications(limit);
    }

    @PostMapping("/api/me/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markNotificationRead(@PathVariable UUID id) {
        community.markNotificationRead(id);
    }

    @GetMapping("/api/me/notifications/unread-count")
    public long unreadNotificationsCount() {
        return community.unreadNotificationsCount();
    }

    @GetMapping("/api/communities/{communityId}/stats")
    public CommunityService.CommunityStats stats(@PathVariable UUID communityId) {
        return community.stats(communityId);
    }
}
