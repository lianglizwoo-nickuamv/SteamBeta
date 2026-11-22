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
 * Controlador de Intercambio Universitario (FXML) — SteamBeta Campus:
 * Catálogo de donación, préstamo y trueque de materiales de estudio.
 * Sin TextInputDialog ni Alert nativos — usa PublishMaterialDialog.fxml.
 * Categorías V11: BOOK, CALCULATOR, LAB_EQUIPMENT, NOTES, TOOL, OTHER
 */
public final class ExchangeController {

    @FXML private Button filterAllBtn;
    @FXML private Button filterBooksBtn;
    @FXML private Button filterToolsBtn;
    @FXML private Button filterNotesBtn;
    @FXML private VBox exchangeListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private String categoryFilter = null;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        loadExchanges();
    }

    private void loadExchanges() {
        long epoch = scope.generation();
        workers.submit(() -> {
            try {
                var items = api.exchangeItems(categoryFilter);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    exchangeListBox.getChildren().clear();

                    if (items.isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("🔄", "No hay materiales disponibles en esta categoría",
                                "Sé el primero en publicar un libro, apunte o calculadora.",
                                "+ Publicar Material", this::onPublish);
                        exchangeListBox.getChildren().add(empty.root());
                    } else {
                        for (var item : items) {
                            var card = ViewLoader.<ExchangeCardController>load("ExchangeCard.fxml");
                            card.controller().set(item);
                            exchangeListBox.getChildren().add(card.root());
                        }
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    @FXML
    private void onFilterAll() {
        categoryFilter = null;
        updateButtons(filterAllBtn, filterBooksBtn, filterToolsBtn, filterNotesBtn);
        loadExchanges();
    }

    @FXML
    private void onFilterBooks() {
        // Categoría exacta V11: BOOK (singular)
        categoryFilter = "BOOK";
        updateButtons(filterBooksBtn, filterAllBtn, filterToolsBtn, filterNotesBtn);
        loadExchanges();
    }

    @FXML
    private void onFilterTools() {
        // Categoría exacta V11: TOOL (singular)
        categoryFilter = "TOOL";
        updateButtons(filterToolsBtn, filterAllBtn, filterBooksBtn, filterNotesBtn);
        loadExchanges();
    }

    @FXML
    private void onFilterNotes() {
        // Categoría exacta V11: NOTES
        categoryFilter = "NOTES";
        updateButtons(filterNotesBtn, filterAllBtn, filterBooksBtn, filterToolsBtn);
        loadExchanges();
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
        loadExchanges();
    }

    @FXML
    private void onPublish() {
        var loader = ViewLoader.<PublishMaterialDialogController>load("PublishMaterialDialog.fxml");
        Stage owner = exchangeListBox.getScene() != null ? (Stage) exchangeListBox.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Publicar Material de Intercambio", owner);

        loader.controller().init(api, workers, () -> {
            loadExchanges();
            UiHelper.showSuccess(owner, "Material Publicado",
                    "Tu material ha sido publicado exitosamente en el catálogo de intercambio.");
        });

        modal.showAndWait();
    }
}
