package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Controlador de Tarjeta de Evento / Fecha Clave (FXML) — SteamBeta Campus.
 */
public final class EventCardController {

    @FXML
    private Label typeBadge;
    @FXML
    private Label commBadge;
    @FXML
    private Label dateLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private Label descLabel;

    private String eventTitle;
    private String eventTypeLabel;
    private String eventCommunity;
    private String eventDateFormatted;
    private String eventDescription;

    public void set(ApiClient.UpcomingEvent ev) {
        apply(ev.title(), ev.eventType(),
                ev.communityName() != null ? ev.communityName() : "Comunidad",
                ev.eventDate(), ev.description());
    }

    public void set(ApiClient.EventItem ev) {
        apply(ev.title(), ev.eventType(),
                ev.creatorAlias() != null ? "Organiza: " + ev.creatorAlias() : "Comunidad",
                ev.eventDate(), ev.description());
    }

    private void apply(String title, String rawType, String communityText, String dateIso, String desc) {
        String normalized = rawType != null ? rawType.toUpperCase() : "";
        String typeClass = switch (normalized) {
            case "EXAM" -> "badge-pending";
            case "DELIVERY", "TASK" -> "badge-admin";
            default -> "badge-role";
        };
        this.eventTypeLabel = switch (normalized) {
            case "EXAM" -> "Examen";
            case "DELIVERY" -> "Entrega";
            case "WORKSHOP" -> "Taller";
            case "MEETING" -> "Reunión";
            case "REMINDER" -> "Recordatorio";
            case "TASK" -> "Tarea personal";
            default -> "Evento";
        };
        this.eventTitle = title != null ? title : "Evento";
        this.eventCommunity = communityText;
        this.eventDateFormatted = UiHelper.formatDate(dateIso);
        this.eventDescription = (desc != null && !desc.isBlank()) ? desc : "Sin descripción adicional.";

        typeBadge.setText(eventTypeLabel);
        typeBadge.getStyleClass().removeAll("badge-pending", "badge-admin", "badge-role");
        typeBadge.getStyleClass().add(typeClass);

        commBadge.setText(communityText);
        dateLabel.setText(eventDateFormatted);
        titleLabel.setText(eventTitle);
        descLabel.setText(eventDescription);
    }

    @FXML
    private void onCardClicked() {
        Stage owner = titleLabel.getScene() != null ? (Stage) titleLabel.getScene().getWindow() : null;
        String detail = "Tipo: " + eventTypeLabel
                + "\nComunidad / Origen: " + eventCommunity
                + "\nFecha y hora: " + eventDateFormatted
                + "\n\nDescripción:\n" + eventDescription;
        UiHelper.showInformation(owner, eventTitle, detail);
    }
}
