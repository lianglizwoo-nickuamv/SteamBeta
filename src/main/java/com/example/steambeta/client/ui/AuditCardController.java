package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador de Tarjeta de Auditoría (FXML) — SteamBeta Campus.
 */
public final class AuditCardController {

    @FXML private Label actionBadge;
    @FXML private Label modLabel;
    @FXML private Label dateLabel;
    @FXML private Label targetReasonLabel;

    public void set(ApiClient.AuditLogEntry log) {
        actionBadge.setText(log.action());
        modLabel.setText("Moderador: " + log.moderatorAlias());
        dateLabel.setText(UiHelper.formatDate(log.createdAt()));
        targetReasonLabel.setText("Destinatario: @" + log.targetLogin() + " · Motivo: " + log.reason());
    }
}
