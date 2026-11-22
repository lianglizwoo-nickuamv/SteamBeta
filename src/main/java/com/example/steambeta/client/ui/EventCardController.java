package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador de Tarjeta de Evento / Fecha Clave (FXML) — SteamBeta Campus.
 */
public final class EventCardController {

    @FXML private Label typeBadge;
    @FXML private Label commBadge;
    @FXML private Label dateLabel;
    @FXML private Label titleLabel;
    @FXML private Label descLabel;

    public void set(ApiClient.UpcomingEvent ev) {
        String typeClass = switch (ev.eventType() != null ? ev.eventType().toUpperCase() : "") {
            case "EXAM" -> "badge-pending";
            case "SUBMISSION" -> "badge-admin";
            default -> "badge-role";
        };

        typeBadge.setText(ev.eventType() != null ? ev.eventType() : "EVENTO");
        typeBadge.getStyleClass().removeAll("badge-pending", "badge-admin", "badge-role");
        typeBadge.getStyleClass().add(typeClass);

        commBadge.setText("Comunidad: " + (ev.communityName() != null ? ev.communityName() : "General"));
        dateLabel.setText(UiHelper.formatDate(ev.eventDate()));
        titleLabel.setText(ev.title());
        descLabel.setText(ev.description() != null ? ev.description() : "");
    }

    public void set(ApiClient.EventItem ev) {
        String typeClass = switch (ev.eventType() != null ? ev.eventType().toUpperCase() : "") {
            case "EXAM" -> "badge-pending";
            case "SUBMISSION" -> "badge-admin";
            default -> "badge-role";
        };

        typeBadge.setText(ev.eventType() != null ? ev.eventType() : "EVENTO");
        typeBadge.getStyleClass().removeAll("badge-pending", "badge-admin", "badge-role");
        typeBadge.getStyleClass().add(typeClass);

        commBadge.setText("Comunidad");
        dateLabel.setText(UiHelper.formatDate(ev.eventDate()));
        titleLabel.setText(ev.title());
        descLabel.setText(ev.description() != null ? ev.description() : "");
    }
}
