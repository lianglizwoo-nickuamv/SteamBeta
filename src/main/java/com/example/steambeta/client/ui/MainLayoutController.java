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
    @FXML private Button notifBtn;
    @FXML private Button profileBtn;
    @FXML private Button logoutBtn;

    @FXML private VBox sidebar;
    @FXML private Button navHome;
    @FXML private Button navCommunities;
    @FXML private Button navQuestions;
    @FXML private Button navLibrary;
    @FXML private Button navCalendar;

    @FXML private Button navTasks;
    @FXML private Button navStudyTeams;
    @FXML private Button navExchange;
    @FXML private Button navOpportunities;

    @FXML private Button navNotifications;
    @FXML private Button navProfile;
    @FXML private Button navSettings;

    @FXML private VBox adminSection;
    @FXML private Button navControlCenter;

    @FXML private BorderPane contentArea;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private Runnable onLogout;
    private final Map<String, Button> navMap = new HashMap<>();

    @FXML
    public void initialize() {
        navMap.put("HOME", navHome);
        navMap.put("COMMUNITIES", navCommunities);
        navMap.put("QUESTIONS", navQuestions);
        navMap.put("LIBRARY", navLibrary);
        navMap.put("CALENDAR", navCalendar);
        navMap.put("TASKS", navTasks);
        navMap.put("STUDY_TEAMS", navStudyTeams);
        navMap.put("EXCHANGE", navExchange);
        navMap.put("OPPORTUNITIES", navOpportunities);
        navMap.put("NOTIFICATIONS", navNotifications);
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

        // Diagnóstico real de conectividad asíncrona y conteo de notificaciones
        workers.submit(() -> {
            try {
                long unread = api.unreadNotificationsCount();
                Platform.runLater(() -> {
                    connectionBadge.setText("● En línea: " + env.getDisplayName());
                    connectionBadge.getStyleClass().setAll("badge-resolved");
                    if (unread > 0) {
                        notifBtn.setText("Avisos (" + unread + ")");
                    } else {
                        notifBtn.setText("Avisos");
                    }
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
                var view = ViewLoader.<HomeController>load("Home.fxml");
                view.controller().init(api, scope, workers, this);
                contentArea.setCenter(view.root());
            }
            case "COMMUNITIES" -> {
                var view = ViewLoader.<CommunitiesController>load("Communities.fxml");
                view.controller().init(api, scope, workers, this);
                contentArea.setCenter(view.root());
            }
            case "QUESTIONS" -> {
                var view = ViewLoader.<AcademicQuestionsController>load("AcademicQuestions.fxml");
                view.controller().init(api, scope, workers, this);
                contentArea.setCenter(view.root());
            }
            case "LIBRARY" -> {
                var view = ViewLoader.<AcademicLibraryController>load("AcademicLibrary.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "CALENDAR" -> {
                var view = ViewLoader.<CalendarController>load("Calendar.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "TASKS" -> {
                var view = ViewLoader.<AcademicPlannerController>load("AcademicPlanner.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "STUDY_TEAMS" -> {
                var view = ViewLoader.<StudyTeamsController>load("StudyTeams.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "EXCHANGE" -> {
                var view = ViewLoader.<ExchangeController>load("Exchange.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "OPPORTUNITIES" -> {
                var view = ViewLoader.<OpportunitiesController>load("Opportunities.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "NOTIFICATIONS" -> {
                var view = ViewLoader.<NotificationCenterController>load("Notifications.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "PROFILE" -> {
                var view = ViewLoader.<ProfileController>load("Profile.fxml");
                view.controller().init(api, scope, workers, this);
                contentArea.setCenter(view.root());
            }
            case "SETTINGS" -> {
                var view = ViewLoader.<SettingsController>load("Settings.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            case "CONTROL_CENTER" -> {
                var view = ViewLoader.<ControlCenterController>load("ControlCenter.fxml");
                view.controller().init(api, scope, workers);
                contentArea.setCenter(view.root());
            }
            default -> navigateTo("HOME");
        }
    }

    public void openCommunityDetail(ApiClient.Community community) {
        var view = ViewLoader.<CommunityDetailController>load("CommunityDetail.fxml");
        view.controller().init(community, api, scope, workers, new CommunityDetailController.CommunityDetailNavigationListener() {
            @Override public void onBackToHome() { navigateTo("COMMUNITIES"); }
            @Override public void onOpenModeration(ApiClient.Community comm) { openCommunityModeration(comm); }
        });
        contentArea.setCenter(view.root());
    }

    public void openCommunityModeration(ApiClient.Community community) {
        scope.reset();
        contentArea.setBottom(null);

        var view = ViewLoader.<CommunityModerationController>load("CommunityModeration.fxml");
        view.controller().init(api, scope, workers, community, this);
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
    @FXML private void onNavNotifications() { navigateTo("NOTIFICATIONS"); }
    @FXML private void onNavProfile() { navigateTo("PROFILE"); }
    @FXML private void onNavSettings() { navigateTo("SETTINGS"); }
    @FXML private void onNavControlCenter() { navigateTo("CONTROL_CENTER"); }

    @FXML private void onSearchAction() { navigateTo("COMMUNITIES"); }
    @FXML private void onNotificationsClicked() { navigateTo("NOTIFICATIONS"); }
    @FXML private void onProfileClicked() { navigateTo("PROFILE"); }
    @FXML private void onLogoutClicked() { if (onLogout != null) onLogout.run(); }
}
