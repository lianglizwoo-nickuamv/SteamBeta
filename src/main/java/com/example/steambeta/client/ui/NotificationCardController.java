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
    @FXML private Label statusBadge;
    @FXML private Label originLabel;
    @FXML private Label dateLabel;
    @FXML private Label titleLabel;
    @FXML private Label msgLabel;
    @FXML private HBox actionRow;

    private Runnable onMarkRead;

    public void set(ApiClient.NotificationItem n, Runnable onMarkRead) {
        this.onMarkRead = onMarkRead;

        cardRoot.getStyleClass().remove("notification-unread");
        if (!n.read()) {
            cardRoot.getStyleClass().add("notification-unread");
            if (statusBadge != null) {
                statusBadge.setText("Sin leer");
                statusBadge.getStyleClass().setAll("badge-pending");
            }
            actionRow.setVisible(true);
            actionRow.setManaged(true);
        } else {
            if (statusBadge != null) {
                statusBadge.setText("Leída");
                statusBadge.getStyleClass().setAll("badge-role");
            }
            actionRow.setVisible(false);
            actionRow.setManaged(false);
        }

        String rawType = n.type() != null ? n.type().toUpperCase() : "";
        typeBadge.setText(typeLabel(rawType));
        typeBadge.getStyleClass().setAll(typeStyleClass(rawType));

        if (originLabel != null) {
            if (n.sourceActorAlias() != null && !n.sourceActorAlias().isBlank()) {
                originLabel.setText("De: " + n.sourceActorAlias());
                originLabel.setVisible(true);
                originLabel.setManaged(true);
            } else {
                originLabel.setVisible(false);
                originLabel.setManaged(false);
            }
        }

        dateLabel.setText(UiHelper.formatDate(n.createdAt()));
        titleLabel.setText(n.title());
        msgLabel.setText(n.message());
    }

    private static String typeLabel(String rawType) {
        return switch (rawType) {
            case "COMMENT_REPLY" -> "RESPUESTA EN COMENTARIO";
            case "QUESTION_ANSWERED" -> "PREGUNTA RESPONDIDA";
            case "ANSWER_ACCEPTED" -> "RESPUESTA ACEPTADA";
            case "COMMUNITY_INVITE" -> "INVITACIÓN A COMUNIDAD";
            case "MODERATION_ALERT" -> "AVISO DE MODERACIÓN";
            case "EVENT_REMINDER" -> "RECORDATORIO DE EVENTO";
            default -> "AVISO";
        };
    }

    private static String typeStyleClass(String rawType) {
        return switch (rawType) {
            case "COMMENT_REPLY", "QUESTION_ANSWERED" -> "badge-academic";
            case "ANSWER_ACCEPTED" -> "badge-resolved";
            case "MODERATION_ALERT" -> "badge-danger";
            case "EVENT_REMINDER" -> "badge-pending";
            default -> "badge-role";
        };
    }

    @FXML
    private void onMarkRead() {
        if (onMarkRead != null) {
            onMarkRead.run();
        }
    }
}
