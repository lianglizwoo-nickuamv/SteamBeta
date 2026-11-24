package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Controlador del diálogo de publicación y edición de oportunidades campus (FXML).
 * Etiquetas en español mapeadas a enums persistentes V11/V15.
 */
public final class PublishOpportunityDialogController {

    private static final Map<String, String> CAT_TO_ENUM = Map.of(
            "Beca", "SCHOLARSHIP",
            "Pasantía", "INTERNSHIP",
            "Curso / Taller", "COURSE",
            "Concurso", "CONTEST",
            "Convocatoria", "CALL",
            "Voluntariado", "VOLUNTEER",
            "Evento", "EVENT",
            "Empleo", "JOB"
    );

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
    private ApiClient.OpportunityItem editingItem;

    @FXML
    public void initialize() {
        categoryCombo.setItems(FXCollections.observableArrayList(
                "Beca", "Pasantía", "Concurso", "Evento", "Curso / Taller", "Convocatoria", "Voluntariado", "Empleo"
        ));
        categoryCombo.setValue("Beca");
    }

    public void init(ApiClient api, ExecutorService workers, Runnable onSuccess) {
        this.api = api;
        this.workers = workers;
        this.onSuccess = onSuccess;
        this.editingItem = null;
    }

    public void initForEdit(ApiClient api, ExecutorService workers, ApiClient.OpportunityItem item, Runnable onSuccess) {
        this.api = api;
        this.workers = workers;
        this.onSuccess = onSuccess;
        this.editingItem = item;
        if (item != null) {
            titleField.setText(item.title() != null ? item.title() : "");
            descriptionArea.setText(item.description() != null ? item.description() : "");
            categoryCombo.setValue(categoryLabel(item.category()));
            institutionField.setText(item.sourceInstitution() != null ? item.sourceInstitution() : "");
            urlField.setText(item.sourceUrl() != null ? item.sourceUrl() : "");
            if (item.deadline() != null && item.deadline().length() >= 10) {
                deadlineField.setText(item.deadline().substring(0, 10));
            }
            btnSubmit.setText("Guardar Cambios");
        }
    }

    public static String categoryLabel(String code) {
        if (code == null) return "Convocatoria";
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "SCHOLARSHIP" -> "Beca";
            case "INTERNSHIP" -> "Pasantía";
            case "COURSE" -> "Curso / Taller";
            case "CONTEST" -> "Concurso";
            case "CALL" -> "Convocatoria";
            case "VOLUNTEER" -> "Voluntariado";
            case "EVENT" -> "Evento";
            case "JOB" -> "Empleo";
            default -> "Convocatoria";
        };
    }

    @FXML
    private void onSubmit() {
        String title = titleField.getText() == null ? "" : titleField.getText().strip();
        if (title.length() < 3 || title.length() > 160) {
            showError("El título debe tener entre 3 y 160 caracteres.");
            return;
        }

        String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().strip();
        if (description.length() < 10 || description.length() > 5000) {
            showError("La descripción debe tener entre 10 y 5000 caracteres.");
            return;
        }

        String category = CAT_TO_ENUM.getOrDefault(categoryCombo.getValue(), "SCHOLARSHIP");

        String institution = institutionField.getText() == null ? "" : institutionField.getText().strip();
        if (institution.length() < 2 || institution.length() > 160) {
            showError("La entidad convocante debe tener entre 2 y 160 caracteres.");
            return;
        }

        String url = urlField.getText() == null ? "" : urlField.getText().strip();
        if (!url.isBlank() && !url.matches("https?://[^\\s/$.?#].[^\\s]*")) {
            showError("El enlace oficial debe comenzar con https:// o http:// y tener formato válido.");
            return;
        }
        final String finalUrl = url.isBlank() ? null : url;

        String deadlineRaw = deadlineField.getText() == null ? "" : deadlineField.getText().strip();
        String deadline = null;
        if (!deadlineRaw.isBlank()) {
            if (!deadlineRaw.matches("\\d{4}-\\d{2}-\\d{2}")) {
                showError("La fecha de cierre debe estar en formato AAAA-MM-DD (ej. 2026-12-31).");
                return;
            }
            deadline = deadlineRaw + "T23:59:59Z";
        }

        btnSubmit.setDisable(true);
        btnCancel.setDisable(true);
        showInfo(editingItem != null ? "Guardando cambios…" : "Publicando convocatoria en PostgreSQL…");

        final String finalDeadline = deadline;
        workers.submit(() -> {
            try {
                if (editingItem != null) {
                    api.updateOpportunity(editingItem.id(), title, description, category, institution, finalUrl, finalDeadline);
                } else {
                    api.createOpportunity(title, description, category, institution, finalUrl, finalDeadline);
                }
                Platform.runLater(() -> {
                    if (onSuccess != null) onSuccess.run();
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSubmit.setDisable(false);
                    btnCancel.setDisable(false);
                    showError(ex.getMessage() != null ? ex.getMessage() : "No se pudo guardar la convocatoria.");
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
