package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Controlador del diálogo de creación de tareas académicas — SteamBeta Campus.
 * Sustituye el TextInputDialog nativo. 100% FXML + CSS.
 */
public final class CreateTaskDialogController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private TextField dueDateField;
    @FXML private TextField projectField;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private ApiClient api;
    private ExecutorService workers;
    private Consumer<ApiClient.TaskItem> onSuccess;

    @FXML
    public void initialize() {
        priorityCombo.setItems(FXCollections.observableArrayList("MEDIUM", "LOW", "HIGH", "URGENT"));
        priorityCombo.setValue("MEDIUM");
    }

    public void init(ApiClient api, ExecutorService workers, Consumer<ApiClient.TaskItem> onSuccess) {
        this.api = api;
        this.workers = workers;
        this.onSuccess = onSuccess;
    }

    @FXML
    private void onSubmit() {
        String title = titleField.getText() == null ? "" : titleField.getText().strip();
        if (title.length() < 2 || title.length() > 160) {
            showError("El título debe tener entre 2 y 160 caracteres.");
            return;
        }

        String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().strip();
        if (description.length() > 3000) {
            showError("La descripción no puede superar 3000 caracteres.");
            return;
        }

        String priority = priorityCombo.getValue() != null ? priorityCombo.getValue() : "MEDIUM";

        String dueDateRaw = dueDateField.getText() == null ? "" : dueDateField.getText().strip();
        String dueDate = null;
        if (!dueDateRaw.isBlank()) {
            if (!dueDateRaw.matches("\\d{4}-\\d{2}-\\d{2}")) {
                showError("La fecha límite debe estar en formato AAAA-MM-DD (ej. 2026-12-31).");
                return;
            }
            dueDate = dueDateRaw + "T23:59:59Z";
        }

        String project = projectField.getText() == null ? "General" : projectField.getText().strip();
        if (project.isBlank()) project = "General";
        if (project.length() > 100) {
            showError("El nombre del proyecto no puede superar 100 caracteres.");
            return;
        }

        btnSubmit.setDisable(true);
        btnCancel.setDisable(true);
        showInfo("Registrando tarea en PostgreSQL…");

        final String finalTitle = title;
        final String finalDesc = description;
        final String finalPriority = priority;
        final String finalDue = dueDate;
        final String finalProject = project;

        workers.submit(() -> {
            try {
                var task = api.createTask(finalTitle, finalDesc, finalPriority, finalDue, finalProject);
                Platform.runLater(() -> {
                    if (onSuccess != null) onSuccess.accept(task);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSubmit.setDisable(false);
                    btnCancel.setDisable(false);
                    showError(ex.getMessage() != null ? ex.getMessage() : "No se pudo registrar la tarea.");
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
