package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Calendario y Fechas Clave (FXML) — SteamBeta Campus:
 * Carga eventos institucionales, entregas y exámenes desde PostgreSQL.
 */
public final class CalendarController {

    @FXML private Button filterAllBtn;
    @FXML private Button filterExamsBtn;
    @FXML private Button filterSubsBtn;
    @FXML private Button filterMeetingsBtn;
    @FXML private Label statusLabel;
    @FXML private VBox eventsListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private String filterType = null;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        loadEvents();
    }

    private void loadEvents() {
        long epoch = scope.generation();
        statusLabel.setText("Consultando agenda en PostgreSQL…");

        workers.submit(() -> {
            try {
                var events = api.upcomingEvents();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    eventsListBox.getChildren().clear();

                    var filtered = events.stream()
                            .filter(e -> filterType == null || filterType.equalsIgnoreCase(e.eventType()))
                            .toList();

                    if (filtered.isEmpty()) {
                        statusLabel.setText("");
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("📅", "No hay fechas clave pendientes",
                                "No se registran eventos próximos en el cronograma de tus comunidades.", null, null);
                        eventsListBox.getChildren().add(empty.root());
                    } else {
                        statusLabel.setText("Mostrando " + filtered.size() + " evento(s) próximo(s):");
                        for (var ev : filtered) {
                            var evCard = ViewLoader.<EventCardController>load("EventCard.fxml");
                            evCard.controller().set(ev);
                            eventsListBox.getChildren().add(evCard.root());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    statusLabel.setText("No se pudieron cargar los eventos del calendario.");
                });
            }
        });
    }

    @FXML
    private void onFilterAll() {
        filterType = null;
        updateButtons(filterAllBtn, filterExamsBtn, filterSubsBtn, filterMeetingsBtn);
        loadEvents();
    }

    @FXML
    private void onFilterExams() {
        filterType = "EXAM";
        updateButtons(filterExamsBtn, filterAllBtn, filterSubsBtn, filterMeetingsBtn);
        loadEvents();
    }

    @FXML
    private void onFilterSubs() {
        filterType = "SUBMISSION";
        updateButtons(filterSubsBtn, filterAllBtn, filterExamsBtn, filterMeetingsBtn);
        loadEvents();
    }

    @FXML
    private void onFilterMeetings() {
        filterType = "MEETING";
        updateButtons(filterMeetingsBtn, filterAllBtn, filterExamsBtn, filterSubsBtn);
        loadEvents();
    }

    private void updateButtons(Button active, Button... others) {
        active.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        active.getStyleClass().add("tab-btn-active");
        for (var b : others) {
            b.getStyleClass().removeAll("tab-btn", "tab-btn-active");
            b.getStyleClass().add("tab-btn");
        }
    }

    @FXML
    private void onRefresh() {
        loadEvents();
    }
}
