package com.example.steambeta.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/**
 * Controlador para el diálogo de formular pregunta académica (FXML).
 */
public final class AskQuestionDialogController {

    @FXML private Label headerTitle;
    @FXML private Label headerSubtitle;
    @FXML private TextField titleField;
    @FXML private TextField subjectField;
    @FXML private TextArea bodyArea;

    public void setCommunity(String communityName) {
        headerSubtitle.setText("Publica tu duda en la comunidad: " + communityName);
    }

    public String getTitle() {
        return titleField.getText().strip();
    }

    public String getSubject() {
        String s = subjectField.getText().strip();
        return s.isBlank() ? "General" : s;
    }

    public String getBody() {
        return bodyArea.getText().strip();
    }
}
