package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Centro de Notificaciones (FXML) — SteamBeta Campus:
 * Carga de avisos de campus, agrupación por estado/recencia y marcado de lectura.
 */
public final class NotificationCenterController {

    @FXML private Button filterAllBtn;
    @FXML private Button filterUnreadBtn;
    @FXML private Label statusLabel;
    @FXML private VBox notifsListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private Runnable onUnreadChanged;
    private boolean unreadOnly = false;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        init(api, scope, workers, null);
    }

    public void init(ApiClient api, SessionScope scope, ExecutorService workers, Runnable onUnreadChanged) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.onUnreadChanged = onUnreadChanged;

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
                    renderNotifications(items);
                    if (onUnreadChanged != null) {
                        onUnreadChanged.run();
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

    private void renderNotifications(List<ApiClient.NotificationItem> items) {
        notifsListBox.getChildren().clear();

        var filtered = items.stream()
                .filter(n -> !unreadOnly || !n.read())
                .toList();

        if (filtered.isEmpty()) {
            statusLabel.setText(unreadOnly ? "0 notificaciones sin leer" : "0 notificaciones registradas");
            var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
            empty.controller().set("",
                    unreadOnly ? "No tienes notificaciones sin leer" : "No hay notificaciones registradas",
                    unreadOnly
                            ? "Todos tus avisos existentes están marcados como leídos."
                            : "Cuando el servidor registre avisos para tu cuenta, aparecerán organizados aquí por estado y recencia.",
                    null, null);
            notifsListBox.getChildren().add(empty.root());
            return;
        }

        var unreadItems = filtered.stream().filter(n -> !n.read()).toList();
        var readItems = filtered.stream().filter(ApiClient.NotificationItem::read).toList();

        if (unreadOnly) {
            statusLabel.setText("Mostrando " + unreadItems.size() + " notificación(es) sin leer");
            appendGroup("NO LEÍDAS (" + unreadItems.size() + ")", unreadItems);
        } else {
            statusLabel.setText(filtered.size() + " notificación(es) · "
                    + unreadItems.size() + " sin leer, " + readItems.size() + " leída(s)");
            if (!unreadItems.isEmpty()) {
                appendGroup("NO LEÍDAS (" + unreadItems.size() + ")", unreadItems);
            }
            if (!readItems.isEmpty()) {
                appendGroup("LEÍDAS (" + readItems.size() + ")", readItems);
            }
        }
    }

    private void appendGroup(String groupTitle, List<ApiClient.NotificationItem> groupItems) {
        Label header = new Label(groupTitle);
        header.getStyleClass().add("notification-section-header");
        notifsListBox.getChildren().add(header);

        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        var recent = groupItems.stream().filter(n -> isRecent(n.createdAt(), cutoff)).toList();
        var older = groupItems.stream().filter(n -> !isRecent(n.createdAt(), cutoff)).toList();

        if (!recent.isEmpty() && !older.isEmpty()) {
            Label recentLbl = new Label("Recientes (últimas 24 h)");
            recentLbl.getStyleClass().add("muted");
            notifsListBox.getChildren().add(recentLbl);
            for (var n : recent) {
                addNotificationCard(n);
            }

            Label olderLbl = new Label("Anteriores");
            olderLbl.getStyleClass().add("muted");
            notifsListBox.getChildren().add(olderLbl);
            for (var n : older) {
                addNotificationCard(n);
            }
        } else {
            for (var n : groupItems) {
                addNotificationCard(n);
            }
        }
    }

    private void addNotificationCard(ApiClient.NotificationItem n) {
        var card = ViewLoader.<NotificationCardController>load("NotificationCard.fxml");
        card.controller().set(n, () -> markNotificationRead(n.id()));
        notifsListBox.getChildren().add(card.root());
    }

    private static boolean isRecent(String iso, Instant cutoff) {
        if (iso == null || iso.isBlank()) return false;
        try {
            return Instant.parse(iso).isAfter(cutoff);
        } catch (Exception ignored) {
            return false;
        }
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
        UiHelper.setActiveTab(filterAllBtn, filterAllBtn, filterUnreadBtn);
        loadNotifications();
    }

    @FXML
    private void onFilterUnread() {
        unreadOnly = true;
        UiHelper.setActiveTab(filterUnreadBtn, filterAllBtn, filterUnreadBtn);
        loadNotifications();
    }

    @FXML
    private void onRefresh() {
        loadNotifications();
    }
}
