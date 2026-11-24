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
    @FXML private Button filterMyPostsBtn;
    @FXML private VBox exchangeListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private String categoryFilter = null;
    private boolean myPostsOnly = false;

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
                var items = api.exchangeItems(categoryFilter, myPostsOnly);
                var cached = api.getCachedProfile();
                String myAccountId = cached != null ? cached.accountId() : null;

                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    exchangeListBox.getChildren().clear();

                    if (items.isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        String emptyTitle = myPostsOnly
                                ? "No tienes materiales publicados aún"
                                : "No hay materiales disponibles en esta categoría";
                        String emptyDesc = myPostsOnly
                                ? "Pulsa '+ Publicar Material' para compartir libros o apuntes con la comunidad."
                                : "Sé el primero en publicar un libro, apunte o calculadora.";
                        empty.controller().set("🔄", emptyTitle, emptyDesc,
                                "+ Publicar Material", this::onPublish);
                        exchangeListBox.getChildren().add(empty.root());
                    } else {
                        for (var item : items) {
                            boolean isOwner = myAccountId != null && myAccountId.equalsIgnoreCase(item.ownerAccountId());
                            var card = ViewLoader.<ExchangeCardController>load("ExchangeCard.fxml");
                            card.controller().set(item, isOwner,
                                    () -> openDetail(item, isOwner),
                                    () -> editItem(item),
                                    () -> deleteItem(item));
                            exchangeListBox.getChildren().add(card.root());
                        }
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    exchangeListBox.getChildren().clear();
                    var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                    String msg = ex.getMessage() != null ? ex.getMessage() : "Error al conectar con el servidor.";
                    empty.controller().set("✕", "No se pudieron cargar los materiales", msg, "Reintentar", this::loadExchanges);
                    exchangeListBox.getChildren().add(empty.root());
                });
            }
        });
    }

    @FXML
    private void onFilterAll() {
        categoryFilter = null;
        myPostsOnly = false;
        updateButtons(filterAllBtn, filterBooksBtn, filterToolsBtn, filterNotesBtn, filterMyPostsBtn);
        loadExchanges();
    }

    @FXML
    private void onFilterBooks() {
        categoryFilter = "BOOK";
        myPostsOnly = false;
        updateButtons(filterBooksBtn, filterAllBtn, filterToolsBtn, filterNotesBtn, filterMyPostsBtn);
        loadExchanges();
    }

    @FXML
    private void onFilterTools() {
        categoryFilter = "TOOL";
        myPostsOnly = false;
        updateButtons(filterToolsBtn, filterAllBtn, filterBooksBtn, filterNotesBtn, filterMyPostsBtn);
        loadExchanges();
    }

    @FXML
    private void onFilterNotes() {
        categoryFilter = "NOTES";
        myPostsOnly = false;
        updateButtons(filterNotesBtn, filterAllBtn, filterBooksBtn, filterToolsBtn, filterMyPostsBtn);
        loadExchanges();
    }

    @FXML
    private void onFilterMyPosts() {
        categoryFilter = null;
        myPostsOnly = true;
        updateButtons(filterMyPostsBtn, filterAllBtn, filterBooksBtn, filterToolsBtn, filterNotesBtn);
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

    private void openDetail(ApiClient.ExchangeItem item, boolean isOwner) {
        var loader = ViewLoader.<ExchangeDetailDialogController>load("ExchangeDetailDialog.fxml");
        Stage owner = exchangeListBox.getScene() != null ? (Stage) exchangeListBox.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Detalle de Material", owner);
        loader.controller().init(api, workers, item, isOwner, this::loadExchanges);
        modal.showAndWait();
    }

    private void editItem(ApiClient.ExchangeItem item) {
        var loader = ViewLoader.<PublishMaterialDialogController>load("PublishMaterialDialog.fxml");
        Stage owner = exchangeListBox.getScene() != null ? (Stage) exchangeListBox.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Editar Material de Intercambio", owner);
        loader.controller().initForEdit(api, workers, item, () -> {
            loadExchanges();
            UiHelper.showSuccess(owner, "Material Actualizado", "Tus cambios han sido guardados correctamente.");
        });
        modal.showAndWait();
    }

    private void deleteItem(ApiClient.ExchangeItem item) {
        Stage owner = exchangeListBox.getScene() != null ? (Stage) exchangeListBox.getScene().getWindow() : null;
        if (!UiHelper.showConfirm(owner, "Eliminar publicación", "¿Deseas eliminar '" + item.title() + "' y sus adjuntos?")) {
            return;
        }
        workers.submit(() -> {
            try {
                api.deleteExchangeItem(item.id());
                Platform.runLater(() -> {
                    loadExchanges();
                    UiHelper.showSuccess(owner, "Publicación Eliminada", "'" + item.title() + "' fue eliminada.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> UiHelper.showError(owner, "Error al eliminar",
                        ex.getMessage() != null ? ex.getMessage() : "No se pudo eliminar la publicación."));
            }
        });
    }
}
