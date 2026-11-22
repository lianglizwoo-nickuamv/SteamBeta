package com.example.steambeta.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public final class MessageDialogController {

    @FXML private Label iconLabel;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnOk;

    private boolean confirmed = false;

    public void set(String icon, String title, String message, boolean isConfirm) {
        if (icon != null && !icon.isBlank()) iconLabel.setText(icon);
        if (title != null && !title.isBlank()) titleLabel.setText(title);
        if (message != null) messageLabel.setText(message);

        btnCancel.setVisible(isConfirm);
        btnCancel.setManaged(isConfirm);
        btnOk.setText(isConfirm ? "Confirmar" : "Entendido");
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    @FXML
    private void onOk() {
        confirmed = true;
        close();
    }

    @FXML
    private void onCancel() {
        confirmed = false;
        close();
    }

    private void close() {
        if (btnOk.getScene() != null && btnOk.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }
}
