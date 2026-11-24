package com.example.steambeta.client;

import com.example.steambeta.client.ui.ViewLoader;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class FxmlVerificationTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyStarted) {
            // Ya inicializado
        }
    }

    @Test
    void verifyAllFxmlFilesLoadSuccessfully() {
        String[] requiredFxmls = {
                "AcademicLibrary.fxml",
                "AcademicPlanner.fxml",
                "AcademicQuestions.fxml",
                "ActivityCard.fxml",
                "AskQuestionDialog.fxml",
                "AuditCard.fxml",
                "Auth.fxml",
                "Calendar.fxml",
                "CalendarDayCell.fxml",
                "CalendarEventTag.fxml",
                "CommentItem.fxml",
                "Communities.fxml",
                "CommunityCard.fxml",
                "CommunityCreatedSuccessDialog.fxml",
                "CommunityDetail.fxml",
                "CommunityMembersHeader.fxml",
                "CommunityModeration.fxml",
                "CommunityOverviewCard.fxml",
                "ControlCenter.fxml",
                "CreateCommunityDialog.fxml",
                "CreateEventDialog.fxml",
                "CreateTaskDialog.fxml",
                "EditProfileDialog.fxml",
                "EditStudyProfileDialog.fxml",
                "EmptyState.fxml",
                "EventCard.fxml",
                "Exchange.fxml",
                "ExchangeCard.fxml",
                "ExchangeDetailDialog.fxml",
                "ExploreCommunitiesDialog.fxml",
                "HelpCenterDialog.fxml",
                "Home.fxml",
                "JoinCommunityDialog.fxml",
                "MainLayout.fxml",
                "MemberCard.fxml",
                "MessageDialog.fxml",
                "NotificationCard.fxml",
                "Notifications.fxml",
                "Opportunities.fxml",
                "OpportunityCard.fxml",
                "PostCard.fxml",
                "Profile.fxml",
                "PublishMaterialDialog.fxml",
                "PublishOpportunityDialog.fxml",
                "PublishResourceDialog.fxml",
                "QuestionCard.fxml",
                "RegisterSubjectDialog.fxml",
                "ReportCard.fxml",
                "ReportContentDialog.fxml",
                "ResourceCard.fxml",
                "Settings.fxml",
                "StudentCard.fxml",
                "StudyMatchCard.fxml",
                "StudyProfileDetailDialog.fxml",
                "StudyTeams.fxml",
                "SubjectCell.fxml",
                "TaskCard.fxml"
        };

        List<String> failedFxmls = new ArrayList<>();

        for (String fxml : requiredFxmls) {
            try {
                var res = ViewLoader.load(fxml);
                assertNotNull(res.root(), "Root node must not be null for " + fxml);
            } catch (Exception ex) {
                failedFxmls.add(fxml + ": " + ex.getMessage());
            }
        }

        assertTrue(failedFxmls.isEmpty(), "Falló la carga de las siguientes vistas FXML:\n" + String.join("\n", failedFxmls));
    }

    @Test
    void testSlugGeneration() {
        assertEquals("club-de-algoritmos", ApiClient.toSlug("Club de Algoritmos"));
        assertEquals("semillero-ia", ApiClient.toSlug("  Semillero IA!!  "));
        assertEquals("fisica-mecanica", ApiClient.toSlug("Física Mecánica"));
        assertEquals("ia-comunidad", ApiClient.toSlug("IA")); // Se asegura longitud >= 3
    }
}
