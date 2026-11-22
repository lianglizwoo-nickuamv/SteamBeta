package com.example.steambeta.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controlador de Estado Vacío (FXML) — SteamBeta Campus:
 * Proporciona mensajes visuales declarativos para secciones sin datos.
 */
public final class EmptyStateController {

    @FXML private Label iconLabel;
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Button actionBtn;

    public void set(String icon, String title, String subtitle, String buttonText, Runnable onAction) {
        if (icon != null) iconLabel.setText(icon);
        if (title != null) titleLabel.setText(title);
        if (subtitle != null) subtitleLabel.setText(subtitle);
        if (buttonText != null && onAction != null) {
            actionBtn.setText(buttonText);
            actionBtn.setVisible(true);
            actionBtn.setManaged(true);
            actionBtn.setOnAction(e -> onAction.run());
        } else {
            actionBtn.setVisible(false);
            actionBtn.setManaged(false);
        }
    }
}
