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
 * Permite buscar compañeros por nombre, alias, materias, temas o apoyo,
 * consultar sus fichas públicas y contactarlos únicamente por los medios habilitados.
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

        // Cargar ficha propia del estudiante autenticado
        workers.submit(() -> {
            try {
                var p = api.studyProfile();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    this.currentProfile = p;
                    myProfileCard.getChildren().clear();

                    ViewLoader.ViewResult<StudyMatchCardController> card = ViewLoader.load("StudyMatchCard.fxml");
                    card.controller().setOwnProfile(p);
                    myProfileCard.getChildren().add(card.root());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    myProfileCard.getChildren().clear();
                    ViewLoader.ViewResult<EmptyStateController> empty = ViewLoader.load("EmptyState.fxml");
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "Error al consultar tu ficha de estudio.";
                    empty.controller().set("✕", "No se pudo cargar tu ficha de estudio", msg, null, null);
                    myProfileCard.getChildren().add(empty.root());
                });
            }
        });

        String currentQuery = subjectSearchField != null && subjectSearchField.getText() != null
                ? subjectSearchField.getText().strip() : "";
        searchMatches(currentQuery);
    }

    private void searchMatches(String query) {
        long epoch = scope.generation();
        String cleanQuery = query == null ? "" : query.strip();

        workers.submit(() -> {
            try {
                var matches = api.findStudyMatches(cleanQuery);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    matchesListBox.getChildren().clear();

                    if (matches.isEmpty()) {
                        ViewLoader.ViewResult<EmptyStateController> empty = ViewLoader.load("EmptyState.fxml");
                        if (!cleanQuery.isBlank()) {
                            empty.controller().set("◆", "Sin coincidencias para «" + cleanQuery + "»",
                                    "No se encontraron compañeros visibles que coincidan con ese nombre, alias, materia o tema.",
                                    "Limpiar búsqueda", this::onClearSearch);
                        } else {
                            empty.controller().set("◆", "Aún no hay compañeros visibles disponibles",
                                    "Comparte tu ficha de estudio como visible para que otros estudiantes puedan encontrarte.",
                                    null, null);
                        }
                        matchesListBox.getChildren().add(empty.root());
                    } else {
                        for (var m : matches) {
                            ViewLoader.ViewResult<StudyMatchCardController> card = ViewLoader.load("StudyMatchCard.fxml");
                            card.controller().setMatch(m,
                                    () -> openStudyProfileDetail(m, false),
                                    () -> openStudyProfileDetail(m, true));
                            matchesListBox.getChildren().add(card.root());
                        }
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    matchesListBox.getChildren().clear();
                    ViewLoader.ViewResult<EmptyStateController> empty = ViewLoader.load("EmptyState.fxml");
                    String msg = ex.getMessage() != null ? ex.getMessage() : "Error al conectar con el servidor.";
                    empty.controller().set("✕", "No se pudieron consultar los compañeros", msg, null, null);
                    matchesListBox.getChildren().add(empty.root());
                });
            }
        });
    }

    private void openStudyProfileDetail(ApiClient.StudyProfile match, boolean focusContact) {
        ViewLoader.ViewResult<StudyProfileDetailDialogController> dialog = ViewLoader.load("StudyProfileDetailDialog.fxml");
        dialog.controller().load(api, workers, match, focusContact);
        Stage owner = myProfileCard.getScene() != null ? (Stage) myProfileCard.getScene().getWindow() : null;
        String title = focusContact ? "Contactar a " + match.alias() : "Ficha de Estudio · " + match.alias();
        UiHelper.createModalStage(dialog.root(), title, owner).showAndWait();
    }

    @FXML
    private void onSearch() {
        String q = subjectSearchField.getText() == null ? "" : subjectSearchField.getText().strip();
        searchMatches(q);
    }

    @FXML
    private void onClearSearch() {
        subjectSearchField.clear();
        searchMatches("");
    }

    @FXML
    private void onRefresh() {
        loadProfileAndMatches();
    }

    @FXML
    private void onEditProfile() {
        ViewLoader.ViewResult<EditStudyProfileDialogController> dialogLoader = ViewLoader.load("EditStudyProfileDialog.fxml");
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
