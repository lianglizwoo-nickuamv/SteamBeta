package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.concurrent.ExecutorService;

/**
 * Controlador del diálogo de publicación de oportunidades campus — SteamBeta Campus.
 * Sustituye el TextInputDialog nativo. Categorías compatibles con V11 PostgreSQL.
 * Categorías: SCHOLARSHIP, INTERNSHIP, COURSE, CONTEST, CALL, VOLUNTEER, EVENT, JOB
 */
public final class PublishOpportunityDialogController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextField institutionField;
    @FXML private TextField urlField;
    @FXML private TextField deadlineField;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private ApiClient api;
    private ExecutorService workers;
    private Runnable onSuccess;

    @FXML
    public void initialize() {
        categoryCombo.setItems(FXCollections.observableArrayList(
                "SCHOLARSHIP", "INTERNSHIP", "COURSE", "CONTEST", "CALL", "VOLUNTEER", "EVENT", "JOB"
        ));
        categoryCombo.setValue("SCHOLARSHIP");
    }

    public void init(ApiClient api, ExecutorService workers, Runnable onSuccess) {
        this.api = api;
        this.workers = workers;
        this.onSuccess = onSuccess;
    }

    @FXML
    private void onSubmit() {
        String title = titleField.getText() == null ? "" : titleField.getText().strip();
        if (title.length() < 3 || title.length() > 160) {
            showError("El título debe tener entre 3 y 160 caracteres.");
            return;
        }

        String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().strip();
        if (description.length() < 5) {
            showError("La descripción debe tener al menos 5 caracteres.");
            return;
        }
        if (description.length() > 4000) {
            showError("La descripción no puede superar 4000 caracteres.");
            return;
        }

        String category = categoryCombo.getValue() != null ? categoryCombo.getValue() : "SCHOLARSHIP";

        String institution = institutionField.getText() == null ? "" : institutionField.getText().strip();
        if (institution.length() < 2 || institution.length() > 160) {
            showError("La institución debe tener entre 2 y 160 caracteres.");
            return;
        }

        String url = urlField.getText() == null ? "" : urlField.getText().strip();
        if (!url.isBlank() && !url.matches("https?://[\\w\\-./+%?=&@#:,;]+")) {
            showError("El enlace debe comenzar con https:// o http:// y ser una URL válida.");
            return;
        }
        final String finalUrl = url.isBlank() ? null : url;

        String deadlineRaw = deadlineField.getText() == null ? "" : deadlineField.getText().strip();
        String deadline = null;
        if (!deadlineRaw.isBlank()) {
            if (!deadlineRaw.matches("\\d{4}-\\d{2}-\\d{2}")) {
                showError("La fecha límite debe estar en formato AAAA-MM-DD (ej. 2026-12-31).");
                return;
            }
            deadline = deadlineRaw + "T23:59:59Z";
        }

        btnSubmit.setDisable(true);
        btnCancel.setDisable(true);
        showInfo("Publicando convocatoria en PostgreSQL…");

        final String finalTitle = title;
        final String finalDesc = description;
        final String finalCategory = category;
        final String finalInstitution = institution;
        final String finalDeadline = deadline;

        workers.submit(() -> {
            try {
                api.createOpportunity(finalTitle, finalDesc, finalCategory, finalInstitution, finalUrl, finalDeadline);
                Platform.runLater(() -> {
                    if (onSuccess != null) onSuccess.run();
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSubmit.setDisable(false);
                    btnCancel.setDisable(false);
                    showError(ex.getMessage() != null ? ex.getMessage() : "No se pudo publicar la convocatoria.");
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
        errorLabel.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold;");
        errorLabel.setVisible(true);
    }

    private void showInfo(String msg) {
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #38bdf8;");
        errorLabel.setVisible(true);
    }

    private void close() {
        if (titleField.getScene() != null && titleField.getScene().getWindow() instanceof Stage s) {
            s.close();
        }
    }
}
