package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Equipos de Estudio (FXML) — SteamBeta Campus:
 * Conecta estudiantes según materias de apoyo mutuo y compatibilidad horaria.
 * Construcción 100% basada en FXML (Sin TextInputDialog ni Alert nativos).
 */
public final class StudyTeamsController {

    @FXML private TextField subjectSearchField;
    @FXML private VBox myProfileCard;
    @FXML private VBox matchesListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private ApiClient.StudyProfile currentProfile;

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
                    this.currentProfile = p;
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
                        empty.controller().set("◆", "No se encontraron compañeros para esta materia",
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
        var dialogLoader = ViewLoader.<EditStudyProfileDialogController>load("EditStudyProfileDialog.fxml");
        if (currentProfile != null) {
            dialogLoader.controller().setProfile(currentProfile);
        }

        Stage owner = myProfileCard.getScene() != null ? (Stage) myProfileCard.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(dialogLoader.root(), "Editar Ficha de Estudio", owner);

        dialogLoader.controller().init(api, workers, updated -> {
            loadProfileAndMatches();
            UiHelper.showSuccess(owner, "Ficha Actualizada", "Tu disponibilidad y materias de estudio han sido guardadas en PostgreSQL.");
        });

        modal.showAndWait();
    }
}
