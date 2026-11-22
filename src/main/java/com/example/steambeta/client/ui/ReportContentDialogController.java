package com.example.steambeta.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

/**
 * Controlador del diálogo de reporte de contenido — SteamBeta Campus.
 * Sustituye el TextInputDialog nativo. 100% FXML + CSS.
 */
public final class ReportContentDialogController {

    @FXML private Label headerLabel;
    @FXML private TextArea reasonArea;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private String reason;
    private boolean submitted;

    public void setHeader(String header) {
        if (header != null) headerLabel.setText(header);
    }

    public String getReason() {
        return reason;
    }

    public boolean isSubmitted() {
        return submitted;
    }

    @FXML
    private void onSubmit() {
        String text = reasonArea.getText() == null ? "" : reasonArea.getText().strip();
        if (text.length() < 3) {
            errorLabel.setText("El motivo debe tener al menos 3 caracteres.");
            errorLabel.setVisible(true);
            return;
        }
        if (text.length() > 1000) {
            errorLabel.setText("El motivo no puede superar 1000 caracteres.");
            errorLabel.setVisible(true);
            return;
        }
        reason = text;
        submitted = true;
        close();
    }

    @FXML
    private void onCancel() {
        submitted = false;
        close();
    }

    private void close() {
        if (reasonArea.getScene() != null && reasonArea.getScene().getWindow() instanceof Stage s) {
            s.close();
        }
    }
}
