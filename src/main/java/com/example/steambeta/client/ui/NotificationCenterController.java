package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Centro de Notificaciones (FXML) — SteamBeta Campus:
 * Carga de avisos de campus y marcado de lectura.
 */
public final class NotificationCenterController {

    @FXML private Button filterAllBtn;
    @FXML private Button filterUnreadBtn;
    @FXML private Label statusLabel;
    @FXML private VBox notifsListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private boolean unreadOnly = false;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        loadNotifications();
    }

    private void loadNotifications() {
        long epoch = scope.generation();
        statusLabel.setText("Consultando avisos en el servidor central…");

        workers.submit(() -> {
            try {
                var items = api.notifications(40);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    notifsListBox.getChildren().clear();

                    var filtered = items.stream()
                            .filter(n -> !unreadOnly || !n.read())
                            .toList();

                    if (filtered.isEmpty()) {
                        statusLabel.setText("");
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("🔔",
                                unreadOnly ? "No tienes notificaciones sin leer" : "Bandeja de avisos vacía",
                                "Cuando tus compañeros respondan tus dudas o publiquen avisos importantes, aparecerán aquí.",
                                null, null);
                        notifsListBox.getChildren().add(empty.root());
                    } else {
                        statusLabel.setText("Mostrando " + filtered.size() + " notificación(es):");
                        for (var n : filtered) {
                            var card = ViewLoader.<NotificationCardController>load("NotificationCard.fxml");
                            card.controller().set(n, () -> markNotificationRead(n.id()));
                            notifsListBox.getChildren().add(card.root());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    statusLabel.setText("No se pudieron cargar las notificaciones.");
                });
            }
        });
    }

    private void markNotificationRead(String id) {
        workers.submit(() -> {
            try {
                api.markNotificationRead(id);
                Platform.runLater(this::loadNotifications);
            } catch (Exception ignored) {}
        });
    }

    @FXML
    private void onFilterAll() {
        unreadOnly = false;
        filterAllBtn.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        filterAllBtn.getStyleClass().add("tab-btn-active");
        filterUnreadBtn.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        filterUnreadBtn.getStyleClass().add("tab-btn");
        loadNotifications();
    }

    @FXML
    private void onFilterUnread() {
        unreadOnly = true;
        filterUnreadBtn.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        filterUnreadBtn.getStyleClass().add("tab-btn-active");
        filterAllBtn.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        filterAllBtn.getStyleClass().add("tab-btn");
        loadNotifications();
    }

    @FXML
    private void onRefresh() {
        loadNotifications();
    }
}
