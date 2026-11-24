package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador de celda para lista de materias (FXML).
 * Distingue claramente materias institucionales y materias registradas manualmente.
 */
public final class SubjectCellController {

    @FXML private Label codeLabel;
    @FXML private Label sourceBadge;
    @FXML private Label nameLabel;
    @FXML private Label metaLabel;

    public void set(ApiClient.Subject subject) {
        codeLabel.setText(subject.code() != null && !subject.code().isBlank() ? subject.code() : "OFICIAL");
        nameLabel.setText(subject.name());
        metaLabel.setText("Período: " + subject.termCode() + " · Sección: " + subject.sectionCode());
        sourceBadge.setText("Institucional");
        sourceBadge.getStyleClass().setAll("badge-admin");
    }

    public void setManual(ApiClient.ManualSubject subject) {
        codeLabel.setText(subject.code() != null && !subject.code().isBlank() ? subject.code() : "PERSONAL");
        nameLabel.setText(subject.name());
        StringBuilder meta = new StringBuilder();
        meta.append(subject.academicYear()).append(" · ").append(subject.termLabel());
        if (subject.groupSection() != null && !subject.groupSection().isBlank()) {
            meta.append(" · Grupo ").append(subject.groupSection());
        }
        metaLabel.setText(meta.toString());
        sourceBadge.setText("Registrada manualmente");
        sourceBadge.getStyleClass().setAll("badge-role");
    }
}
