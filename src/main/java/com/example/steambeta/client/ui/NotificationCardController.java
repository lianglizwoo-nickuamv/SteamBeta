package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Controlador de Tarjeta de Notificación (FXML) — SteamBeta Campus.
 */
public final class NotificationCardController {

    @FXML private VBox cardRoot;
    @FXML private Label typeBadge;
    @FXML private Label dateLabel;
    @FXML private Label titleLabel;
    @FXML private Label msgLabel;
    @FXML private HBox actionRow;

    private Runnable onMarkRead;

    public void set(ApiClient.NotificationItem n, Runnable onMarkRead) {
        this.onMarkRead = onMarkRead;

        if (!n.read()) {
            cardRoot.getStyleClass().add("notification-unread");
            actionRow.setVisible(true);
            actionRow.setManaged(true);
        } else {
            actionRow.setVisible(false);
            actionRow.setManaged(false);
        }

        typeBadge.setText(n.type() != null ? n.type() : "AVISO");
        dateLabel.setText(UiHelper.formatDate(n.createdAt()));
        titleLabel.setText(n.title());
        msgLabel.setText(n.message());
    }

    @FXML
    private void onMarkRead() {
        if (onMarkRead != null) {
            onMarkRead.run();
        }
    }
}
