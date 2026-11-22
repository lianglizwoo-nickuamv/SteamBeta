package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Controlador para el diálogo de edición de ficha de equipos de estudio (FXML).
 * Reemplaza el TextInputDialog nativo con formulario completo y fuertemente tipado.
 */
public final class EditStudyProfileDialogController {

    @FXML private TextField subjectsField;
    @FXML private TextField interestsField;
    @FXML private TextField canHelpField;
    @FXML private TextField needsHelpField;
    @FXML private ComboBox<String> availabilityCombo;
    @FXML private CheckBox visibleCheck;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSave;

    private ApiClient api;
    private ExecutorService workers;
    private Consumer<ApiClient.StudyProfile> onSaved;

    @FXML
    public void initialize() {
        availabilityCombo.setItems(FXCollections.observableArrayList(
                "Tardes",
                "Mañanas",
                "Noches",
                "Fines de semana",
                "Horario flexible"
        ));
        availabilityCombo.getSelectionModel().selectFirst();
    }

    public void init(ApiClient api, ExecutorService workers, Consumer<ApiClient.StudyProfile> onSaved) {
        this.api = api;
        this.workers = workers;
        this.onSaved = onSaved;
    }

    public void setProfile(ApiClient.StudyProfile p) {
        if (p == null) return;
        if (p.subjects() != null && !p.subjects().isEmpty()) {
            subjectsField.setText(String.join(", ", p.subjects()));
        }
        if (p.interests() != null && !p.interests().isEmpty()) {
            interestsField.setText(String.join(", ", p.interests()));
        }
        canHelpField.setText(p.canHelpWith() != null ? p.canHelpWith() : "");
        needsHelpField.setText(p.needsHelpWith() != null ? p.needsHelpWith() : "");
        if (p.availability() != null && !p.availability().isBlank()) {
            availabilityCombo.getSelectionModel().select(p.availability());
        }
        visibleCheck.setSelected(p.visible());
    }

    @FXML
    private void onSave() {
        String subsText = subjectsField.getText() != null ? subjectsField.getText().strip() : "";
        List<String> subjects = Arrays.stream(subsText.split(","))
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .toList();

        if (subjects.isEmpty()) {
            showError("Debes especificar al menos una materia de estudio.");
            return;
        }

        String intsText = interestsField.getText() != null ? interestsField.getText().strip() : "";
        List<String> interests = Arrays.stream(intsText.split(","))
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .toList();

        String canHelp = canHelpField.getText() != null && !canHelpField.getText().isBlank()
                ? canHelpField.getText().strip() : "Apoyo general";
        String needsHelp = needsHelpField.getText() != null && !needsHelpField.getText().isBlank()
                ? needsHelpField.getText().strip() : "Resolución de ejercicios";
        String avail = availabilityCombo.getValue() != null ? availabilityCombo.getValue() : "Tardes";
        boolean visible = visibleCheck.isSelected();

        if (api == null || workers == null) return;

        btnSave.setDisable(true);
        btnCancel.setDisable(true);
        showStatus("Actualizando ficha en PostgreSQL...", "status-pending");

        workers.submit(() -> {
            try {
                var updated = api.updateStudyProfile(subjects, interests, canHelp, needsHelp, avail, visible);
                Platform.runLater(() -> {
                    if (onSaved != null) onSaved.accept(updated);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSave.setDisable(false);
                    btnCancel.setDisable(false);
                    showError(ex.getMessage() != null ? ex.getMessage() : "Error al actualizar la ficha de estudio.");
                });
            }
        });
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void showError(String message) {
        showStatus(message, "status-error");
    }

    private void showStatus(String message, String styleClass) {
        errorLabel.setText(message);
        errorLabel.getStyleClass().setAll(styleClass);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void close() {
        if (btnSave.getScene() != null && btnSave.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }
}
