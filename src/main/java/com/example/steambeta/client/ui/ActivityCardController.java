package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador de Tarjeta de Actividad Académica (FXML) — SteamBeta Campus.
 */
public final class ActivityCardController {

    @FXML private Label checkBadge;
    @FXML private Label reqBadge;
    @FXML private Label codeLabel;
    @FXML private Label nameLabel;

    public void set(ApiClient.Activity act) {
        checkBadge.getStyleClass().removeAll("badge-resolved", "badge-pending");
        if (act.completed()) {
            checkBadge.setText("✓ COMPLETADA");
            checkBadge.getStyleClass().add("badge-resolved");
        } else {
            checkBadge.setText("○ PENDIENTE");
            checkBadge.getStyleClass().add("badge-pending");
        }

        reqBadge.getStyleClass().removeAll("badge-admin", "badge-role");
        if (act.required()) {
            reqBadge.setText("OBLIGATORIA");
            reqBadge.getStyleClass().add("badge-admin");
        } else {
            reqBadge.setText("OPCIONAL");
            reqBadge.getStyleClass().add("badge-role");
        }

        codeLabel.setText("Código: " + act.code());
        nameLabel.setText(act.name());
    }
}
