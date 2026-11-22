package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador para tarjeta de recurso académico (FXML).
 */
public final class ResourceCardController {

    @FXML private Label typeBadge;
    @FXML private Label uploaderLabel;
    @FXML private Label dateLabel;
    @FXML private Label titleLabel;
    @FXML private Label descLabel;

    public void set(ApiClient.ResourceItem r) {
        typeBadge.setText(r.resourceType() != null ? r.resourceType() : "RECURSO");
        uploaderLabel.setText("Subido por: " + (r.uploaderAlias() != null ? r.uploaderAlias() : "Estudiante"));
        dateLabel.setText(UiHelper.formatDate(r.createdAt()));
        titleLabel.setText(r.title());
        descLabel.setText(r.description() != null ? r.description() : "");
    }
}
