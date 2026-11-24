package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Controlador para el diálogo modal de formular pregunta académica (FXML).
 * Cierra el modal únicamente tras la confirmación real del backend.
 */
public final class AskQuestionDialogController {

    @FXML private Label headerTitle;
    @FXML private Label headerSubtitle;
    @FXML private TextField titleField;
    @FXML private TextField subjectField;
    @FXML private TextArea bodyArea;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private ApiClient api;
    private ExecutorService workers;
    private ApiClient.Community community;
    private Consumer<ApiClient.Question> onSuccess;

    public void init(ApiClient api, ExecutorService workers, ApiClient.Community community,
                     Consumer<ApiClient.Question> onSuccess) {
        this.api = api;
        this.workers = workers;
        this.community = community;
        this.onSuccess = onSuccess;
        if (community != null) {
            headerSubtitle.setText("Publica tu duda en la comunidad: " + community.name());
        }
    }

    @FXML
    private void onSubmit() {
        if (community == null) {
            showError("No hay una comunidad seleccionada.");
            return;
        }
        String title = titleField.getText() == null ? "" : titleField.getText().strip();
        String body = bodyArea.getText() == null ? "" : bodyArea.getText().strip();
        String subject = subjectField.getText() == null ? "" : subjectField.getText().strip();
        if (subject.isBlank()) subject = "General";

        if (title.length() < 3 || title.length() > 160) {
            showError("El título debe tener entre 3 y 160 caracteres.");
            return;
        }
        if (body.length() < 10 || body.length() > 10000) {
            showError("La descripción debe tener entre 10 y 10 000 caracteres.");
            return;
        }
        if (subject.length() < 2 || subject.length() > 80) {
            showError("La asignatura debe tener entre 2 y 80 caracteres.");
            return;
        }

        btnSubmit.setDisable(true);
        btnCancel.setDisable(true);
        showInfo("Publicando pregunta en PostgreSQL…");

        final String finalSubject = subject;
        workers.submit(() -> {
            try {
                var created = api.createQuestion(community.id(), UUID.randomUUID(), title, body, finalSubject, List.of());
                Platform.runLater(() -> {
                    if (onSuccess != null) onSuccess.accept(created);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSubmit.setDisable(false);
                    btnCancel.setDisable(false);
                    String cause = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "Error del servidor al publicar la pregunta.";
                    showError("No se pudo publicar la pregunta: " + cause);
                });
            }
        });
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.getStyleClass().setAll("status-error");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void showInfo(String msg) {
        errorLabel.setText(msg);
        errorLabel.getStyleClass().setAll("muted");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void close() {
        if (titleField.getScene() != null && titleField.getScene().getWindow() instanceof Stage s) {
            s.close();
        }
    }
}
