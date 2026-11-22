package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public final class CreateCommunityDialogController {

    @FXML private TextField nameField;
    @FXML private TextField slugField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> visibilityCombo;
    @FXML private TextArea descriptionArea;
    @FXML private TextArea rulesArea;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private ApiClient api;
    private ExecutorService workers;
    private Consumer<ApiClient.Community> onSuccess;

    @FXML
    public void initialize() {
        categoryCombo.setItems(FXCollections.observableArrayList(
                "ACADEMICA", "ESTUDIO", "PROYECTO", "CARRERA", "INTERES"
        ));
        categoryCombo.setValue("ACADEMICA");

        visibilityCombo.setItems(FXCollections.observableArrayList(
                "PUBLIC", "INVITE_ONLY", "PRIVATE"
        ));
        visibilityCombo.setValue("PUBLIC");

        rulesArea.setText("Respeta a los miembros y colabora constructivamente.");

        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && (slugField.getText().isBlank() || slugField.getText().equals(ApiClient.toSlug(oldVal)))) {
                slugField.setText(ApiClient.toSlug(newVal));
            }
        });
    }

    public void init(ApiClient api, ExecutorService workers, Consumer<ApiClient.Community> onSuccess) {
        this.api = api;
        this.workers = workers;
        this.onSuccess = onSuccess;
    }

    public String getName() {
        return nameField.getText() == null ? "" : nameField.getText().strip();
    }

    public String getSlug() {
        String s = slugField.getText() == null ? "" : slugField.getText().strip();
        return s.isBlank() ? ApiClient.toSlug(getName()) : s;
    }

    public String getCategory() {
        return categoryCombo.getValue() != null ? categoryCombo.getValue() : "ACADEMICA";
    }

    public String getVisibility() {
        return visibilityCombo.getValue() != null ? visibilityCombo.getValue() : "PUBLIC";
    }

    public String getDescription() {
        return descriptionArea.getText() == null ? "" : descriptionArea.getText().strip();
    }

    public String getRules() {
        String r = rulesArea.getText() == null ? "" : rulesArea.getText().strip();
        return r.isBlank() ? "Respeta a los miembros y colabora constructivamente." : r;
    }

    public boolean validate() {
        String n = getName();
        if (n.length() < 3 || n.length() > 100) {
            showError("El nombre debe tener entre 3 y 100 caracteres.");
            return false;
        }
        String s = getSlug();
        if (!s.matches("^[a-z0-9][a-z0-9-]{2,79}$")) {
            showError("El slug identificador debe tener entre 3 y 80 caracteres en minúsculas y guiones (ej. club-de-ia).");
            return false;
        }
        errorLabel.setVisible(false);
        return true;
    }

    @FXML
    private void onSubmit() {
        if (!validate()) return;
        if (api == null || workers == null) return;

        btnSubmit.setDisable(true);
        btnCancel.setDisable(true);
        showError("Creando comunidad universitaria en PostgreSQL...");
        errorLabel.setStyle("-fx-text-fill: #38bdf8;");

        String name = getName();
        String slug = getSlug();
        String category = getCategory();
        String visibility = getVisibility();
        String description = getDescription();
        String rules = getRules();

        workers.submit(() -> {
            try {
                var item = api.createCommunity(slug, name, description, rules, category, visibility);
                Platform.runLater(() -> {
                    if (onSuccess != null) onSuccess.accept(item);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSubmit.setDisable(false);
                    btnCancel.setDisable(false);
                    showError(ex.getMessage());
                });
            }
        });
    }

    @FXML
    private void onCancel() {
        close();
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold;");
        errorLabel.setVisible(true);
    }

    private void close() {
        if (nameField.getScene() != null && nameField.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }
}
