package com.example.steambeta.backend.study;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/study-teams")
public class StudyTeamController {
    private final StudyTeamService studyTeamService;

    public StudyTeamController(StudyTeamService studyTeamService) {
        this.studyTeamService = studyTeamService;
    }

    @GetMapping("/profile")
    public StudyTeamService.StudyProfile myProfile() {
        return studyTeamService.myProfile();
    }

    @PutMapping("/profile")
    public StudyTeamService.StudyProfile updateProfile(@RequestBody StudyTeamService.ProfileDraft draft) {
        return studyTeamService.updateProfile(draft);
    }

    @GetMapping("/matches")
    public List<StudyTeamService.StudyProfile> findMatches(@RequestParam(required = false) String subject) {
        return studyTeamService.findMatches(subject);
    }
}
