package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador para tarjeta de integrante de comunidad (FXML).
 */
public final class MemberCardController {

    @FXML private Label aliasLabel;
    @FXML private Label roleBadge;
    @FXML private Label universityLabel;

    public void set(ApiClient.Member m) {
        aliasLabel.setText(m.alias() != null ? m.alias() : "Estudiante");
        roleBadge.setText(m.role() != null ? m.role() : "MEMBER");
        roleBadge.getStyleClass().removeAll("badge-admin", "badge-role");
        if ("ADMIN".equals(m.role())) {
            roleBadge.getStyleClass().add("badge-admin");
        } else {
            roleBadge.getStyleClass().add("badge-role");
        }
        universityLabel.setText(m.declaredUniversity() != null ? m.declaredUniversity() : "");
    }
}
