package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Comunidades Universitarias (FXML) — SteamBeta Campus:
 * Gestiona el catálogo de comunidades del estudiante, búsqueda local,
 * creación directa de comunidades, unirse con código y exploración pública.
 */
public final class CommunitiesController {

    @FXML private TextField searchField;
    @FXML private Button catAllBtn;
    @FXML private Button catAcadBtn;
    @FXML private Button catStudyBtn;
    @FXML private Button catProjBtn;
    @FXML private Button catCareerBtn;
    @FXML private Button catInterestBtn;

    @FXML private Label statusLabel;
    @FXML private VBox communityListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private MainLayoutController navigator;

    private List<ApiClient.Community> allCommunities = new ArrayList<>();
    private String selectedCategory = null;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers, MainLayoutController navigator) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.navigator = navigator;

        loadCommunities();
    }

    private void loadCommunities() {
        long epoch = scope.generation();
        statusLabel.setText("Cargando tus comunidades desde PostgreSQL (steam_academico)...");

        workers.submit(() -> {
            try {
                var list = api.communities();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    this.allCommunities = list != null ? list : new ArrayList<>();
                    renderFilteredList();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    statusLabel.setText("No se pudieron cargar tus comunidades. Pulsa 'Actualizar'.");
                });
            }
        });
    }

    private void renderFilteredList() {
        communityListBox.getChildren().clear();
        String query = searchField.getText() != null ? searchField.getText().strip().toLowerCase() : "";

        var filtered = allCommunities.stream()
                .filter(c -> {
                    if (!query.isBlank()) {
                        boolean matchName = c.name() != null && c.name().toLowerCase().contains(query);
                        boolean matchDesc = c.description() != null && c.description().toLowerCase().contains(query);
                        boolean matchSlug = c.slug() != null && c.slug().toLowerCase().contains(query);
                        if (!matchName && !matchDesc && !matchSlug) return false;
                    }
                    return true;
                })
                .toList();

        if (filtered.isEmpty()) {
            statusLabel.setText(allCommunities.isEmpty()
                    ? "Aún no perteneces a ninguna comunidad universitaria."
                    : "No se encontraron comunidades con los criterios seleccionados.");

            var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
            empty.controller().set("●", "Sin comunidades coincidentes",
                    "Crea un nuevo grupo de estudio o únete mediante un código de invitación.",
                    "+ Crear Comunidad", this::onCreateCommunity);
            communityListBox.getChildren().add(empty.root());
        } else {
            statusLabel.setText("Perteneces a " + filtered.size() + " comunidad(es) universitaria(s):");
            for (var comm : filtered) {
                var card = ViewLoader.<CommunityCardController>load("CommunityCard.fxml");
                card.controller().set(comm, c -> {
                    if (navigator != null) navigator.openCommunityDetail(c);
                });
                communityListBox.getChildren().add(card.root());
            }
        }
    }

    @FXML
    private void onSearch() {
        renderFilteredList();
    }

    @FXML
    private void onFilterAll() {
        selectedCategory = null;
        updateCategoryButtons(catAllBtn, catAcadBtn, catStudyBtn, catProjBtn, catCareerBtn, catInterestBtn);
        renderFilteredList();
    }

    @FXML
    private void onFilterAcad() {
        selectedCategory = "ACADEMICA";
        updateCategoryButtons(catAcadBtn, catAllBtn, catStudyBtn, catProjBtn, catCareerBtn, catInterestBtn);
        renderFilteredList();
    }

    @FXML
    private void onFilterStudy() {
        selectedCategory = "ESTUDIO";
        updateCategoryButtons(catStudyBtn, catAllBtn, catAcadBtn, catProjBtn, catCareerBtn, catInterestBtn);
        renderFilteredList();
    }

    @FXML
    private void onFilterProj() {
        selectedCategory = "PROYECTO";
        updateCategoryButtons(catProjBtn, catAllBtn, catAcadBtn, catStudyBtn, catCareerBtn, catInterestBtn);
        renderFilteredList();
    }

    @FXML
    private void onFilterCareer() {
        selectedCategory = "CARRERA";
        updateCategoryButtons(catCareerBtn, catAllBtn, catAcadBtn, catStudyBtn, catProjBtn, catInterestBtn);
        renderFilteredList();
    }

    @FXML
    private void onFilterInterest() {
        selectedCategory = "INTERES";
        updateCategoryButtons(catInterestBtn, catAllBtn, catAcadBtn, catStudyBtn, catProjBtn, catCareerBtn);
        renderFilteredList();
    }

    private void updateCategoryButtons(Button active, Button... others) {
        active.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        active.getStyleClass().add("tab-btn-active");
        for (var b : others) {
            b.getStyleClass().removeAll("tab-btn", "tab-btn-active");
            b.getStyleClass().add("tab-btn");
        }
    }

    @FXML
    private void onRefresh() {
        loadCommunities();
    }

    @FXML
    private void onCreateCommunity() {
        var loader = ViewLoader.<CreateCommunityDialogController>load("CreateCommunityDialog.fxml");
        Stage owner = communityListBox.getScene() != null ? (Stage) communityListBox.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Crear Nueva Comunidad", owner);

        loader.controller().init(api, workers, community -> {
            loadCommunities();
            if (community.initialInvitationCode() != null && !community.initialInvitationCode().isBlank()) {
                var successLoader = ViewLoader.<CommunityCreatedSuccessDialogController>load("CommunityCreatedSuccessDialog.fxml");
                successLoader.controller().set(community);
                var successModal = UiHelper.createModalStage(successLoader.root(), "¡Comunidad Creada Exitosamente!", owner);
                successModal.showAndWait();
            } else {
                UiHelper.showSuccess(owner, "¡Comunidad Creada!",
                        "La comunidad '" + community.name() + "' ha sido creada exitosamente.");
            }
            if (navigator != null) {
                navigator.openCommunityDetail(community);
            }
        });

        modal.showAndWait();
    }

    @FXML
    private void onJoinWithCode() {
        var loader = ViewLoader.<JoinCommunityDialogController>load("JoinCommunityDialog.fxml");
        Stage owner = communityListBox.getScene() != null ? (Stage) communityListBox.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Unirse con Código de Invitación", owner);

        loader.controller().init(api, workers, community -> {
            loadCommunities();
            UiHelper.showSuccess(owner, "¡Bienvenido al Espacio Universitario!",
                    "Te has incorporado exitosamente a la comunidad '" + community.name() + "'.");
            if (navigator != null) {
                navigator.openCommunityDetail(community);
            }
        });

        modal.showAndWait();
    }

    @FXML
    private void onExplorePublic() {
        var loader = ViewLoader.<ExploreCommunitiesDialogController>load("ExploreCommunitiesDialog.fxml");
        Stage owner = communityListBox.getScene() != null ? (Stage) communityListBox.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Catálogo de Comunidades Públicas", owner);
        loader.controller().load(api, workers);
        modal.showAndWait();
    }
}
