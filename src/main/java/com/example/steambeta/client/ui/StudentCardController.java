package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador de Tarjeta de Estudiante (FXML) — SteamBeta Campus.
 */
public final class StudentCardController {

    @FXML private Label nameLoginLabel;
    @FXML private Label roleBadge;
    @FXML private Label statusBadge;
    @FXML private Label detailsLabel;

    public void set(ApiClient.StudentOverview s) {
        nameLoginLabel.setText(s.alias() + " (@" + s.loginIdentifier() + ")");
        roleBadge.setText(s.globalRole());

        statusBadge.getStyleClass().removeAll("badge-resolved", "badge-pending");
        if (s.enabled()) {
            statusBadge.setText("ACTIVA");
            statusBadge.getStyleClass().add("badge-resolved");
        } else {
            statusBadge.setText("INACTIVA / SUSPENDIDA");
            statusBadge.getStyleClass().add("badge-pending");
        }

        detailsLabel.setText("Comunidades: " + s.communitiesCount() + " · Publicaciones: " + s.postsCount() + " · Sanciones activas: " + s.activeSanctionsCount());
    }
}
