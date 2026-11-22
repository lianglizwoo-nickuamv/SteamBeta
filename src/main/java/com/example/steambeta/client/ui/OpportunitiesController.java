package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Oportunidades Campus (FXML) — SteamBeta Campus:
 * Cartelera de convocatorias de becas, pasantías y concursos.
 * Sin TextInputDialog ni Alert nativos — usa PublishOpportunityDialog.fxml.
 */
public final class OpportunitiesController {

    @FXML private Button filterAllBtn;
    @FXML private Button filterScholarshipsBtn;
    @FXML private Button filterInternshipsBtn;
    @FXML private Button filterContestsBtn;
    @FXML private Button filterEventsBtn;
    @FXML private VBox oppListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private String categoryFilter = null;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        loadOpportunities();
    }

    private void loadOpportunities() {
        long epoch = scope.generation();
        workers.submit(() -> {
            try {
                var items = api.opportunities(categoryFilter);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    oppListBox.getChildren().clear();

                    if (items.isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("🚀", "No hay convocatorias activas en esta categoría",
                                "Sé el primero en compartir una beca o pasantía de interés universitario.", null, null);
                        oppListBox.getChildren().add(empty.root());
                    } else {
                        for (var item : items) {
                            var card = ViewLoader.<OpportunityCardController>load("OpportunityCard.fxml");
                            card.controller().set(item);
                            oppListBox.getChildren().add(card.root());
                        }
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    @FXML
    private void onFilterAll() {
        categoryFilter = null;
        updateButtons(filterAllBtn, filterScholarshipsBtn, filterInternshipsBtn, filterContestsBtn, filterEventsBtn);
        loadOpportunities();
    }

    @FXML
    private void onFilterScholarships() {
        categoryFilter = "SCHOLARSHIP";
        updateButtons(filterScholarshipsBtn, filterAllBtn, filterInternshipsBtn, filterContestsBtn, filterEventsBtn);
        loadOpportunities();
    }

    @FXML
    private void onFilterInternships() {
        categoryFilter = "INTERNSHIP";
        updateButtons(filterInternshipsBtn, filterAllBtn, filterScholarshipsBtn, filterContestsBtn, filterEventsBtn);
        loadOpportunities();
    }

    @FXML
    private void onFilterContests() {
        categoryFilter = "CONTEST";
        updateButtons(filterContestsBtn, filterAllBtn, filterScholarshipsBtn, filterInternshipsBtn, filterEventsBtn);
        loadOpportunities();
    }

    @FXML
    private void onFilterEvents() {
        categoryFilter = "EVENT";
        updateButtons(filterEventsBtn, filterAllBtn, filterScholarshipsBtn, filterInternshipsBtn, filterContestsBtn);
        loadOpportunities();
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
        loadOpportunities();
    }

    @FXML
    private void onNewOpportunity() {
        var loader = ViewLoader.<PublishOpportunityDialogController>load("PublishOpportunityDialog.fxml");
        Stage owner = oppListBox.getScene() != null ? (Stage) oppListBox.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Publicar Convocatoria Campus", owner);

        loader.controller().init(api, workers, () -> {
            loadOpportunities();
            UiHelper.showSuccess(owner, "Convocatoria Publicada",
                    "La oportunidad ha sido publicada en el tablero de convocatorias.");
        });

        modal.showAndWait();
    }
}
