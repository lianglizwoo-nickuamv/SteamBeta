package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador de Tarjeta de Coincidencia de Estudio (FXML) — SteamBeta Campus.
 */
public final class StudyMatchCardController {

    @FXML private Label aliasLabel;
    @FXML private Label careerBadge;
    @FXML private Label availBadge;
    @FXML private Label subjectsLabel;
    @FXML private Label helpLabel;

    public void set(ApiClient.StudyProfile m) {
        aliasLabel.setText(m.alias());
        careerBadge.setText(m.declaredCareer() != null ? m.declaredCareer() : "Estudiante");
        availBadge.setText("Disponibilidad: " + (m.availability() != null ? m.availability() : "A convenir"));
        subjectsLabel.setText("Materias: " + String.join(", ", m.subjects()));
        helpLabel.setText("Ofrece apoyo en: " + (m.canHelpWith() != null ? m.canHelpWith() : "Varios temas"));
    }
}
