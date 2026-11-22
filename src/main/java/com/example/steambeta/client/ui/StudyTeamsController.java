package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Equipos de Estudio (FXML) — SteamBeta Campus:
 * Conecta estudiantes según materias de apoyo mutuo y compatibilidad horaria.
 */
public final class StudyTeamsController {

    @FXML private TextField subjectSearchField;
    @FXML private VBox myProfileCard;
    @FXML private VBox matchesListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        loadProfileAndMatches();
    }

    private void loadProfileAndMatches() {
        long epoch = scope.generation();

        // Cargar ficha propia
        workers.submit(() -> {
            try {
                var p = api.studyProfile();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    myProfileCard.getChildren().clear();

                    var card = ViewLoader.<StudyMatchCardController>load("StudyMatchCard.fxml");
                    card.controller().set(p);
                    myProfileCard.getChildren().add(card.root());
                });
            } catch (Exception ignored) {}
        });

        searchMatches("");
    }

    private void searchMatches(String query) {
        long epoch = scope.generation();
        workers.submit(() -> {
            try {
                var matches = api.findStudyMatches(query);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    matchesListBox.getChildren().clear();

                    if (matches.isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("🤝", "No se encontraron compañeros para esta materia",
                                "Prueba con otra materia o comparte tu ficha para que otros te contacten.", null, null);
                        matchesListBox.getChildren().add(empty.root());
                    } else {
                        for (var m : matches) {
                            var card = ViewLoader.<StudyMatchCardController>load("StudyMatchCard.fxml");
                            card.controller().set(m);
                            matchesListBox.getChildren().add(card.root());
                        }
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    @FXML
    private void onSearch() {
        searchMatches(subjectSearchField.getText().strip());
    }

    @FXML
    private void onRefresh() {
        loadProfileAndMatches();
    }

    @FXML
    private void onEditProfile() {
        var dialog = new TextInputDialog();
        dialog.setTitle("Ficha de Estudio");
        dialog.setHeaderText("Materias de estudio separadas por coma");
        dialog.setContentText("Materias:");

        dialog.showAndWait().ifPresent(subsText -> {
            var subs = Arrays.stream(subsText.split(","))
                    .map(String::strip).filter(s -> !s.isBlank()).toList();

            workers.submit(() -> {
                try {
                    api.updateStudyProfile(subs, List.of(), "Apoyo general", "Resolución de ejercicios", "Tardes", true);
                    Platform.runLater(this::loadProfileAndMatches);
                } catch (Exception ex) {
                    Platform.runLater(() -> UiHelper.alert("Error", "No se pudo actualizar la ficha.", Alert.AlertType.ERROR));
                }
            });
        });
    }
}
