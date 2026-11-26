package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.EnvironmentConfig;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Controlador Principal de Layout (FXML) — SteamBeta Campus:
 * Gestiona el Shell persistente, barra de navegación, diagnóstico real de conectividad,
 * conteo asíncrono de notificaciones y enrutamiento hacia los módulos independientes.
 */
public final class MainLayoutController {

    @FXML private BorderPane rootPane;
    @FXML private HBox topBar;
    @FXML private Label connectionBadge;
    @FXML private TextField searchField;
    @FXML private Button profileBtn;
    @FXML private Button logoutBtn;

    @FXML private VBox sidebar;
    @FXML private Button navHome;
    @FXML private Button navCommunities;
    // navQuestions removed from sidebar – Q&A lives inside each community
    @FXML private Button navLibrary;
    @FXML private Button navCalendar;

    @FXML private Button navTasks;
    @FXML private Button navStudyTeams;
    @FXML private Button navExchange;
    @FXML private Button navOpportunities;

    @FXML private Button navProfile;
    @FXML private Button navSettings;

    @FXML private VBox adminSection;
    @FXML private Button navControlCenter;

    @FXML private BorderPane contentArea;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private Runnable onLogout;
    private String currentViewId = "HOME";
    private final Map<String, Button> navMap = new HashMap<>();

    @FXML
    public void initialize() {
        navMap.put("HOME", navHome);
        navMap.put("COMMUNITIES", navCommunities);
        // QUESTIONS route kept for internal use but not in sidebar
        navMap.put("LIBRARY", navLibrary);
        navMap.put("CALENDAR", navCalendar);
        navMap.put("TASKS", navTasks);
        navMap.put("STUDY_TEAMS", navStudyTeams);
        navMap.put("EXCHANGE", navExchange);
        navMap.put("OPPORTUNITIES", navOpportunities);
        navMap.put("PROFILE", navProfile);
        navMap.put("SETTINGS", navSettings);
        navMap.put("CONTROL_CENTER", navControlCenter);
    }

    public void init(ApiClient api, SessionScope scope, ExecutorService workers, Runnable onLogout) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.onLogout = onLogout;

        // Entorno y diagnóstico real de conexión
        var env = EnvironmentConfig.getCurrentEnvironment();
        connectionBadge.setText("○ Verificando: " + env.getDisplayName());
        connectionBadge.getStyleClass().setAll("badge-pending");

        // Alias en botón de perfil
        var profile = api.getCachedProfile();
        if (profile != null && profile.alias() != null && !profile.alias().isBlank()) {
            profileBtn.setText("● " + profile.alias());
        }

        // Permisos de moderación general
        boolean isMod = profile != null && profile.isGlobalModerator();
        if (isMod) {
            adminSection.setVisible(true);
            adminSection.setManaged(true);
        }

        // Diagnóstico real de conectividad asíncrona
        workers.submit(() -> {
            try {
                api.profile();
                Platform.runLater(() -> {
                    connectionBadge.setText("● En línea: " + env.getDisplayName());
                    connectionBadge.getStyleClass().setAll("badge-resolved");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connectionBadge.setText("✕ Sin respuesta: " + env.getDisplayName());
                    connectionBadge.getStyleClass().setAll("badge-danger");
                });
            }
        });

        navigateTo("HOME");
    }

    public void navigateTo(String viewId) {
        this.currentViewId = viewId != null ? viewId : "HOME";
        // Actualizar estilos activos de los botones
        for (var entry : navMap.entrySet()) {
            if (entry.getValue() != null) {
                entry.getValue().getStyleClass().removeAll("nav-item-active", "tab-btn-active", "nav-item", "tab-btn");
                if (entry.getKey().equals(viewId)) {
                    entry.getValue().getStyleClass().add("nav-item-active");
                } else {
                    entry.getValue().getStyleClass().add("nav-item");
                }
            }
        }

        contentArea.getChildren().clear();

        switch (viewId) {
            case "HOME" -> {
                ViewLoader.ViewResult<HomeController> view = ViewLoader.load("Home.fxml");
                view.controller().init(api, scope, workers, this);
                contentArea.setCenter(view.root());
            }
            case "COMMUNITIES" -> {
                ViewLoader.ViewResult<CommunitiesController> view = ViewLoader.load("Communities.fxml");
                view.controller().init(api, scope, workers, this);
                contentArea.setCenter(view.root());
            }
            case "QUESTIONS" -> {
                ViewLoader.ViewResult<AcademicQuestionsController> view = ViewLoader.load("AcademicQuestions.fxml");
                view.controller().init(api, scope, workers, this);
                contentArea.setCenter(view.root());
            }
            case "LIBRARY" -> {
                ViewLoader.ViewResult<AcademicLibraryController> view = ViewLoader.load("AcademicLibrary.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "CALENDAR" -> {
                ViewLoader.ViewResult<CalendarController> view = ViewLoader.load("Calendar.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "TASKS" -> {
                ViewLoader.ViewResult<AcademicPlannerController> view = ViewLoader.load("AcademicPlanner.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "STUDY_TEAMS" -> {
                ViewLoader.ViewResult<StudyTeamsController> view = ViewLoader.load("StudyTeams.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "EXCHANGE" -> {
                ViewLoader.ViewResult<ExchangeController> view = ViewLoader.load("Exchange.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "OPPORTUNITIES" -> {
                ViewLoader.ViewResult<OpportunitiesController> view = ViewLoader.load("Opportunities.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "PROFILE" -> {
                ViewLoader.ViewResult<ProfileController> view = ViewLoader.load("Profile.fxml");
                view.controller().init(api, scope, workers, this);
                contentArea.setCenter(view.root());
            }
            case "SETTINGS" -> {
                ViewLoader.ViewResult<SettingsController> view = ViewLoader.load("Settings.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "CONTROL_CENTER" -> {
                ViewLoader.ViewResult<ControlCenterController> view = ViewLoader.load("ControlCenter.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            default -> navigateTo("HOME");
        }
    }

    public void openCommunityDetail(ApiClient.Community community) {
        var view = ViewLoader.<CommunityDetailController>load("CommunityDetail.fxml");
        view.controller().init(community, api, scope, workers, () -> navigateTo("COMMUNITIES"));
        contentArea.setCenter(view.root());
    }

    @FXML private void onNavHome() { navigateTo("HOME"); }
    @FXML private void onNavCommunities() { navigateTo("COMMUNITIES"); }
    @FXML private void onNavQuestions() { navigateTo("QUESTIONS"); }
    @FXML private void onNavLibrary() { navigateTo("LIBRARY"); }
    @FXML private void onNavCalendar() { navigateTo("CALENDAR"); }
    @FXML private void onNavTasks() { navigateTo("TASKS"); }
    @FXML private void onNavStudyTeams() { navigateTo("STUDY_TEAMS"); }
    @FXML private void onNavExchange() { navigateTo("EXCHANGE"); }
    @FXML private void onNavOpportunities() { navigateTo("OPPORTUNITIES"); }
    @FXML private void onNavProfile() { navigateTo("PROFILE"); }
    @FXML private void onNavSettings() { navigateTo("SETTINGS"); }
    @FXML private void onNavControlCenter() { navigateTo("CONTROL_CENTER"); }

    @FXML
    private void onHelpClicked() {
        var loader = ViewLoader.<HelpCenterDialogController>load("HelpCenterDialog.fxml");
        loader.controller().selectModule(currentViewId);
        javafx.stage.Stage owner = rootPane.getScene() != null ? (javafx.stage.Stage) rootPane.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Centro de Ayuda · SteamBeta Campus", owner);
        modal.showAndWait();
    }

    @FXML private void onSearchAction() { navigateTo("COMMUNITIES"); }
    @FXML private void onProfileClicked() { navigateTo("PROFILE"); }
    @FXML private void onLogoutClicked() { if (onLogout != null) onLogout.run(); }
}
