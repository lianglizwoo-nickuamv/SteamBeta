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
 * Controlador de Preguntas Académicas (FXML) — SteamBeta Campus:
 * Gestiona el filtrado, consulta y formulación de dudas académicas en Q&A.
 */
public final class AcademicQuestionsController {

    @FXML private ComboBox<ApiClient.Community> commCombo;
    @FXML private Button filterAllBtn;
    @FXML private Button filterOpenBtn;
    @FXML private Button filterResolvedBtn;
    @FXML private TextField searchField;
    @FXML private Label statusLabel;
    @FXML private VBox questionsListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private MainLayoutController navigator;

    private ApiClient.Community selectedCommunity;
    private String statusFilter = null;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers, MainLayoutController navigator) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.navigator = navigator;

        setupCombo();
        loadCommunities();
    }

    private void setupCombo() {
        commCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(ApiClient.Community item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        commCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(ApiClient.Community item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Selecciona una comunidad" : item.name());
            }
        });
        commCombo.setOnAction(e -> {
            this.selectedCommunity = commCombo.getSelectionModel().getSelectedItem();
            loadQuestions();
        });
    }

    private void loadCommunities() {
        long epoch = scope.generation();
        workers.submit(() -> {
            try {
                var list = api.communities();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    commCombo.getItems().setAll(list);
                    if (list.isEmpty()) {
                        statusLabel.setText("Aún no perteneces a ninguna comunidad. Únete para interactuar en Q&A.");
                    } else {
                        commCombo.getSelectionModel().selectFirst();
                        this.selectedCommunity = commCombo.getSelectionModel().getSelectedItem();
                        loadQuestions();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("No se pudieron cargar tus comunidades."));
            }
        });
    }

    private void loadQuestions() {
        if (selectedCommunity == null) {
            statusLabel.setText("Selecciona una comunidad para ver sus preguntas.");
            questionsListBox.getChildren().clear();
            return;
        }

        long epoch = scope.generation();
        String query = searchField.getText().strip();
        statusLabel.setText("Cargando preguntas de " + selectedCommunity.name() + "…");

        workers.submit(() -> {
            try {
                var rows = api.questions(selectedCommunity.id(), statusFilter, null, query);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    questionsListBox.getChildren().clear();

                    if (rows.isEmpty()) {
                        statusLabel.setText("");
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("❓", "No se encontraron preguntas académicas",
                                "Sé el primero en formular una consulta o ejercicio sobre esta materia.",
                                "+ Formular Pregunta", this::onNewQuestion);
                        questionsListBox.getChildren().add(empty.root());
                        return;
                    }

                    statusLabel.setText("Mostrando " + rows.size() + " pregunta(s) en " + selectedCommunity.name() + ":");
                    for (var q : rows) {
                        var card = ViewLoader.<QuestionCardController>load("QuestionCard.fxml");
                        card.controller().set(q, () -> {
                            if (selectedCommunity != null) {
                                navigator.openCommunityDetail(selectedCommunity);
                            }
                        });
                        questionsListBox.getChildren().add(card.root());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("No se pudieron cargar las preguntas académicas."));
            }
        });
    }



    @FXML
    private void onFilterAll() {
        statusFilter = null;
        updateButtons(filterAllBtn, filterOpenBtn, filterResolvedBtn);
        loadQuestions();
    }

    @FXML
    private void onFilterOpen() {
        statusFilter = "OPEN";
        updateButtons(filterOpenBtn, filterAllBtn, filterResolvedBtn);
        loadQuestions();
    }

    @FXML
    private void onFilterResolved() {
        statusFilter = "RESOLVED";
        updateButtons(filterResolvedBtn, filterAllBtn, filterOpenBtn);
        loadQuestions();
    }

    private void updateButtons(Button active, Button... others) {
        active.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        active.getStyleClass().add("tab-btn-active");
        for (var b : others) {
            b.getStyleClass().removeAll("tab-btn", "tab-btn-active");
            b.getStyleClass().add("tab-btn");
        }
    }

    @FXML private void onSearch() { loadQuestions(); }
    @FXML private void onRefresh() { loadQuestions(); }

    @FXML
    private void onNewQuestion() {
        if (selectedCommunity == null) {
            UiHelper.alert("Aviso", "Primero selecciona una comunidad universitaria.", Alert.AlertType.INFORMATION);
            return;
        }

        var dialogLoader = ViewLoader.<AskQuestionDialogController>load("AskQuestionDialog.fxml");
        var dialogPane = (DialogPane) dialogLoader.root();
        var controller = dialogLoader.controller();
        controller.setCommunity(selectedCommunity.name());

        var dialog = new Dialog<Boolean>();
        dialog.setTitle("Formular Pregunta Académica");
        dialog.setDialogPane(dialogPane);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String t = controller.getTitle();
                String b = controller.getBody();
                String s = controller.getSubject();

                if (t.length() < 5 || b.length() < 10) {
                    UiHelper.alert("Validación", "Título (mínimo 5 caracteres) y descripción (mínimo 10).", Alert.AlertType.WARNING);
                    return false;
                }

                workers.submit(() -> {
                    try {
                        api.createQuestion(selectedCommunity.id(), UUID.randomUUID(), t, b, s, List.of());
                        Platform.runLater(this::loadQuestions);
                    } catch (Exception ex) {
                        Platform.runLater(() -> UiHelper.alert("Error", "No se pudo publicar la pregunta.", Alert.AlertType.ERROR));
                    }
                });
                return true;
            }
            return false;
        });

        dialog.showAndWait();
    }
}
