package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Portada y Dashboard Personal (FXML) — SteamBeta Campus:
 * Presenta el resumen del estudiante, indicadores en tiempo real de tareas,
 * comunidades, eventos próximos y accesos directos al ecosistema universitario.
 */
public final class HomeController {

    @FXML private Label greetingLabel;
    @FXML private Label studentSubLabel;

    @FXML private Label pendingTasksCountLabel;
    @FXML private Label communitiesCountLabel;
    @FXML private Label upcomingEventsCountLabel;
    @FXML private Label unreadNotificationsCountLabel;

    @FXML private VBox urgentEventsBox;

    @FXML private Label aliasSummaryLabel;
    @FXML private Label uniSummaryLabel;
    @FXML private Label careerSummaryLabel;
    @FXML private Label semesterSummaryLabel;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private MainLayoutController navigator;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers, MainLayoutController navigator) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.navigator = navigator;

        loadDashboard();
    }

    private void loadDashboard() {
        long epoch = scope.generation();

        // 1. Perfil del estudiante para saludo y resumen
        var cached = api.getCachedProfile();
        if (cached != null) {
            updateProfileView(cached);
        }

        workers.submit(() -> {
            try {
                var p = api.profile();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    updateProfileView(p);
                });
            } catch (Exception ignored) {}
        });

        // 2. Conteo de tareas pendientes
        workers.submit(() -> {
            try {
                var tasks = api.myTasks(null);
                long pending = tasks.stream()
                        .filter(t -> !"COMPLETED".equalsIgnoreCase(t.status()))
                        .count();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    pendingTasksCountLabel.setText(String.valueOf(pending));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    pendingTasksCountLabel.setText("0");
                });
            }
        });

        // 3. Conteo de comunidades
        workers.submit(() -> {
            try {
                var comms = api.communities();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    communitiesCountLabel.setText(String.valueOf(comms.size()));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    communitiesCountLabel.setText("0");
                });
            }
        });

        // 4. Próximos eventos y entregas
        workers.submit(() -> {
            try {
                var events = api.upcomingEvents();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    upcomingEventsCountLabel.setText(String.valueOf(events.size()));
                    urgentEventsBox.getChildren().clear();

                    if (events.isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("✓", "Sin entregas inmediatas",
                                "No tienes exámenes ni entregas programadas en los próximos días.", null, null);
                        urgentEventsBox.getChildren().add(empty.root());
                    } else {
                        for (var ev : events.stream().limit(4).toList()) {
                            var evCard = ViewLoader.<EventCardController>load("EventCard.fxml");
                            evCard.controller().set(ev);
                            urgentEventsBox.getChildren().add(evCard.root());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    upcomingEventsCountLabel.setText("0");
                });
            }
        });

        // 5. Conteo de avisos sin leer
        workers.submit(() -> {
            try {
                long unread = api.unreadNotificationsCount();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    unreadNotificationsCountLabel.setText(String.valueOf(unread));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    unreadNotificationsCountLabel.setText("0");
                });
            }
        });
    }

    private void updateProfileView(ApiClient.Profile p) {
        String name = p.alias() != null && !p.alias().isBlank() ? p.alias() : p.login();
        greetingLabel.setText("¡Hola, " + name + "!");
        studentSubLabel.setText("Panel universitario de " + (p.declaredCareer() != null && !p.declaredCareer().isBlank() ? p.declaredCareer() : "estudio") + " en SteamBeta Campus.");

        aliasSummaryLabel.setText(name);
        uniSummaryLabel.setText(p.declaredUniversity() != null && !p.declaredUniversity().isBlank() ? p.declaredUniversity() : "No declarada");
        careerSummaryLabel.setText(p.declaredCareer() != null && !p.declaredCareer().isBlank() ? p.declaredCareer() : "No declarada");
        semesterSummaryLabel.setText(p.declaredSemester() != null ? "Semestre " + p.declaredSemester() : "No especificado");
    }

    @FXML
    private void onRefreshDashboard() {
        loadDashboard();
    }

    @FXML
    private void onOpenCalendar() {
        if (navigator != null) navigator.navigateTo("CALENDAR");
    }

    @FXML
    private void onOpenCommunities() {
        if (navigator != null) navigator.navigateTo("COMMUNITIES");
    }

    @FXML
    private void onOpenTasks() {
        if (navigator != null) navigator.navigateTo("TASKS");
    }

    @FXML
    private void onOpenQuestions() {
        if (navigator != null) navigator.navigateTo("QUESTIONS");
    }

    @FXML
    private void onOpenLibrary() {
        if (navigator != null) navigator.navigateTo("LIBRARY");
    }

    @FXML
    private void onOpenProfile() {
        if (navigator != null) navigator.navigateTo("PROFILE");
    }
}
