package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Intercambio Universitario (FXML) — SteamBeta Campus:
 * Catálogo solidario de intercambio, donación y préstamo de materiales académicos
 * con filtros locales combinables por categoría, modalidad, búsqueda y autor.
 */
public final class ExchangeController {

    private static final String MOD_ALL = "Todas las modalidades";
    private static final String MOD_EXCHANGE = "Intercambio";
    private static final String MOD_GIFT = "Donación";
    private static final String MOD_LOAN = "Préstamo";

    @FXML private Button filterAllBtn;
    @FXML private Button filterBooksBtn;
    @FXML private Button filterToolsBtn;
    @FXML private Button filterNotesBtn;
    @FXML private Button filterMyPostsBtn;
    @FXML private ComboBox<String> modalityFilterCombo;
    @FXML private TextField searchField;
    @FXML private Label statusLabel;
    @FXML private VBox exchangeListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private String categoryFilter = null;
    private boolean myPostsOnly = false;
    private List<ApiClient.ExchangeItem> loadedItems = new ArrayList<>();
    private String myAccountId;
    private boolean suppressEvents = false;

    @FXML
    public void initialize() {
        suppressEvents = true;
        if (modalityFilterCombo != null) {
            modalityFilterCombo.getItems().setAll(MOD_ALL, MOD_EXCHANGE, MOD_GIFT, MOD_LOAN);
            modalityFilterCombo.setValue(MOD_ALL);
        }
        suppressEvents = false;
    }

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        loadExchanges();
    }

    private void loadExchanges() {
        long epoch = scope.generation();
        if (statusLabel != null) statusLabel.setText("Consultando materiales de intercambio…");

        workers.submit(() -> {
            try {
                var items = api.exchangeItems(categoryFilter, myPostsOnly);
                var cached = api.getCachedProfile();
                String currentAccountId = cached != null ? cached.accountId() : null;

                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    this.loadedItems = items != null ? items : new ArrayList<>();
                    this.myAccountId = currentAccountId;
                    renderFilteredItems();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    exchangeListBox.getChildren().clear();
                    if (statusLabel != null) statusLabel.setText("Error al consultar materiales.");
                    var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                    String msg = ex.getMessage() != null ? ex.getMessage() : "Error al conectar con el servidor.";
                    empty.controller().set("✕", "No se pudieron cargar los materiales", msg, "Reintentar", this::loadExchanges);
                    exchangeListBox.getChildren().add(empty.root());
                });
            }
        });
    }

    private void renderFilteredItems() {
        exchangeListBox.getChildren().clear();

        String modSel = modalityFilterCombo != null && modalityFilterCombo.getValue() != null
                ? modalityFilterCombo.getValue() : MOD_ALL;
        String query = searchField != null && searchField.getText() != null
                ? searchField.getText().strip().toLowerCase(Locale.ROOT) : "";

        var filtered = loadedItems.stream()
                .filter(it -> switch (modSel) {
                    case MOD_EXCHANGE -> "EXCHANGE".equalsIgnoreCase(it.exchangeType());
                    case MOD_GIFT -> "GIFT".equalsIgnoreCase(it.exchangeType());
                    case MOD_LOAN -> "LOAN".equalsIgnoreCase(it.exchangeType());
                    default -> true;
                })
                .filter(it -> query.isBlank()
                        || safeLower(it.title()).contains(query)
                        || safeLower(it.description()).contains(query)
                        || safeLower(it.ownerAlias()).contains(query))
                .toList();

        boolean hasExtraFilters = !MOD_ALL.equals(modSel) || !query.isBlank();

        if (filtered.isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText(hasExtraFilters
                        ? "Sin materiales que coincidan con los filtros seleccionados."
                        : "Sin materiales publicados en esta sección.");
            }
            var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
            if (hasExtraFilters) {
                empty.controller().set("◆", "Sin coincidencias",
                        "No se encontraron materiales con esa modalidad o búsqueda. Puedes limpiar los filtros para ver todo.",
                        "Limpiar filtros", this::onClearFilters);
            } else {
                String emptyTitle = myPostsOnly
                        ? "No tienes materiales publicados aún"
                        : "No hay materiales disponibles en esta categoría";
                String emptyDesc = myPostsOnly
                        ? "Pulsa '+ Publicar Material o Libro' para compartir libros, apuntes o herramientas sin dinero de por medio."
                        : "Sé el primero en publicar un libro, apunte o herramienta en intercambio, donación o préstamo.";
                empty.controller().set("●", emptyTitle, emptyDesc,
                        "+ Publicar Material o Libro", this::onPublish);
            }
            exchangeListBox.getChildren().add(empty.root());
            return;
        }

        if (statusLabel != null) {
            statusLabel.setText("Mostrando " + filtered.size() + " material(es) académico(s) solidario(s):");
        }
        for (var item : filtered) {
            boolean isOwner = myAccountId != null && myAccountId.equalsIgnoreCase(item.ownerAccountId());
            var card = ViewLoader.<ExchangeCardController>load("ExchangeCard.fxml");
            card.controller().set(item, isOwner,
                    () -> openDetail(item, isOwner),
                    () -> editItem(item),
                    () -> deleteItem(item));
            exchangeListBox.getChildren().add(card.root());
        }
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    @FXML
    private void onFilterChanged() {
        if (suppressEvents) return;
        renderFilteredItems();
    }

    @FXML
    private void onClearFilters() {
        suppressEvents = true;
        categoryFilter = null;
        myPostsOnly = false;
        updateButtons(filterAllBtn, filterBooksBtn, filterToolsBtn, filterNotesBtn, filterMyPostsBtn);
        if (modalityFilterCombo != null) modalityFilterCombo.setValue(MOD_ALL);
        if (searchField != null) searchField.clear();
        suppressEvents = false;
        loadExchanges();
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
                    "Tu material ha sido publicado exitosamente en el catálogo de intercambio solidario.");
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
