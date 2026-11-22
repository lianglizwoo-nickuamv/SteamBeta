package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador de celda para lista de materias (FXML).
 */
public final class SubjectCellController {

    @FXML private Label codeLabel;
    @FXML private Label nameLabel;
    @FXML private Label metaLabel;

    public void set(ApiClient.Subject subject) {
        codeLabel.setText(subject.code());
        nameLabel.setText(subject.name());
        metaLabel.setText(subject.termCode() + " / " + subject.sectionCode());
    }
}
