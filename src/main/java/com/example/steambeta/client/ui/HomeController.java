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
 * Controlador de Portada y Comunidades (FXML) — SteamBeta Campus:
 * Muestra el resumen de comunidades activas del estudiante y el panel lateral de fechas clave.
 */
public final class HomeController {

    @FXML private Label statusLabel;
    @FXML private VBox communityListBox;
    @FXML private VBox upcomingEventsBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private MainLayoutController navigator;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers, MainLayoutController navigator) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.navigator = navigator;

        loadData();
    }

    private void loadData() {
        long epoch = scope.generation();
        statusLabel.setText("Cargando tus comunidades desde PostgreSQL (steam_academico)…");

        // 1. Cargar comunidades del usuario
        workers.submit(() -> {
            try {
                var list = api.communities();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    communityListBox.getChildren().clear();

                    if (list.isEmpty()) {
                        statusLabel.setText("Aún no perteneces a ninguna comunidad universitaria.");
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("👥", "No tienes comunidades asignadas",
                                "Crea un nuevo grupo de estudio o únete con un código de invitación.",
                                "+ Crear Comunidad", this::onCreateCommunity);
                        communityListBox.getChildren().add(empty.root());
                    } else {
                        statusLabel.setText("Perteneces a " + list.size() + " comunidad(es) universitaria(s):");
                        for (var comm : list) {
                            var card = ViewLoader.<CommunityCardController>load("CommunityCard.fxml");
                            card.controller().set(comm, c -> navigator.openCommunityDetail(c));
                            communityListBox.getChildren().add(card.root());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    statusLabel.setText("No se pudieron cargar tus comunidades. Pulsa 'Actualizar'.");
                });
            }
        });

        // 2. Cargar próximas fechas clave
        workers.submit(() -> {
            try {
                var events = api.upcomingEvents();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    upcomingEventsBox.getChildren().clear();

                    if (events.isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("📅", "Sin fechas clave pendientes", "No hay eventos próximos en tu agenda.", null, null);
                        upcomingEventsBox.getChildren().add(empty.root());
                    } else {
                        for (var ev : events.stream().limit(5).toList()) {
                            var evCard = ViewLoader.<EventCardController>load("EventCard.fxml");
                            evCard.controller().set(ev);
                            upcomingEventsBox.getChildren().add(evCard.root());
                        }
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    @FXML
    private void onRefresh() {
        loadData();
    }

    @FXML
    private void onCreateCommunity() {
        var loader = ViewLoader.<CreateCommunityDialogController>load("CreateCommunityDialog.fxml");
        Stage owner = communityListBox.getScene() != null ? (Stage) communityListBox.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Crear Nueva Comunidad", owner);

        loader.controller().init(api, workers, community -> {
            loadData();
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
            loadData();
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
