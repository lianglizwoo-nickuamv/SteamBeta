package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public final class ExploreCommunitiesDialogController {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilterCombo;
    @FXML private VBox listBox;
    @FXML private Label statusLabel;

    private ApiClient api;
    private ExecutorService workers;
    private final List<ApiClient.CommunityOverview> allCommunities = new ArrayList<>();

    @FXML
    public void initialize() {
        categoryFilterCombo.setItems(FXCollections.observableArrayList(
                "Todas las Categorías", "ACADEMICA", "ESTUDIO", "PROYECTO", "CARRERA", "INTERES"
        ));
        categoryFilterCombo.setValue("Todas las Categorías");
    }

    public void load(ApiClient api, ExecutorService workers) {
        this.api = api;
        this.workers = workers;
        fetchData();
    }

    private void fetchData() {
        statusLabel.setText("Consultando catálogo de comunidades en el servidor...");
        workers.submit(() -> {
            try {
                var overviews = api.exploreCommunities();
                Platform.runLater(() -> {
                    allCommunities.clear();
                    allCommunities.addAll(overviews);
                    renderFiltered();
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error al consultar comunidades: " + e.getMessage()));
            }
        });
    }

    @FXML
    private void onFilter() {
        renderFiltered();
    }

    @FXML
    private void onReset() {
        searchField.setText("");
        categoryFilterCombo.setValue("Todas las Categorías");
        renderFiltered();
    }

    @FXML
    private void onRefresh() {
        fetchData();
    }

    private void renderFiltered() {
        String query = searchField.getText() == null ? "" : searchField.getText().strip().toLowerCase(Locale.ROOT);
        String categoryFilter = categoryFilterCombo.getValue();
        boolean filterCategory = categoryFilter != null && !categoryFilter.equals("Todas las Categorías");

        var filtered = allCommunities.stream()
                .filter(c -> query.isBlank() || (c.name() != null && c.name().toLowerCase(Locale.ROOT).contains(query))
                        || (c.description() != null && c.description().toLowerCase(Locale.ROOT).contains(query))
                        || (c.slug() != null && c.slug().toLowerCase(Locale.ROOT).contains(query)))
                .filter(c -> !filterCategory || categoryFilter.equalsIgnoreCase(c.category()))
                .toList();

        listBox.getChildren().clear();

        if (filtered.isEmpty()) {
            if (allCommunities.isEmpty()) {
                statusLabel.setText("No se encontraron comunidades públicas activas en el campus.");
                var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                empty.controller().set("🏛️", "Sin comunidades públicas",
                        "Aún no existen comunidades abiertas para consulta en la plataforma.", null, null);
                listBox.getChildren().add(empty.root());
            } else {
                statusLabel.setText("No hay resultados para los filtros seleccionados.");
            }
        } else {
            statusLabel.setText("Mostrando " + filtered.size() + " de " + allCommunities.size() + " comunidad(es):");
            for (var ov : filtered) {
                var card = ViewLoader.<CommunityOverviewCardController>load("CommunityOverviewCard.fxml");
                card.controller().set(ov);
                listBox.getChildren().add(card.root());
            }
        }
    }

    @FXML
    private void onClose() {
        if (listBox.getScene() != null && listBox.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }
}
