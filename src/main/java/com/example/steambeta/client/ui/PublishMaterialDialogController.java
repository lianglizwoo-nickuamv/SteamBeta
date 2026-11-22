package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.concurrent.ExecutorService;

/**
 * Controlador del diálogo de publicación de material de intercambio — SteamBeta Campus.
 * Sustituye el TextInputDialog nativo. Categorías y tipos compatibles con V11 PostgreSQL.
 * Categorías: BOOK, CALCULATOR, LAB_EQUIPMENT, NOTES, TOOL, OTHER
 * Tipos: EXCHANGE, GIFT, LOAN
 */
public final class PublishMaterialDialogController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField contactField;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private ApiClient api;
    private ExecutorService workers;
    private Runnable onSuccess;

    @FXML
    public void initialize() {
        // Categorías exactas del CHECK constraint V11
        categoryCombo.setItems(FXCollections.observableArrayList(
                "BOOK", "CALCULATOR", "LAB_EQUIPMENT", "NOTES", "TOOL", "OTHER"
        ));
        categoryCombo.setValue("BOOK");

        // Tipos exactos del CHECK constraint V11
        typeCombo.setItems(FXCollections.observableArrayList(
                "EXCHANGE", "GIFT", "LOAN"
        ));
        typeCombo.setValue("EXCHANGE");
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
        if (description.length() > 3000) {
            showError("La descripción no puede superar 3000 caracteres.");
            return;
        }

        String category = categoryCombo.getValue() != null ? categoryCombo.getValue() : "BOOK";
        String type = typeCombo.getValue() != null ? typeCombo.getValue() : "EXCHANGE";

        String contact = contactField.getText() == null ? "" : contactField.getText().strip();
        if (contact.isBlank()) {
            showError("Indica cómo contactarte para coordinar el intercambio.");
            return;
        }

        btnSubmit.setDisable(true);
        btnCancel.setDisable(true);
        showInfo("Publicando material en PostgreSQL…");

        final String finalTitle = title;
        final String finalDesc = description;
        final String finalCategory = category;
        final String finalType = type;
        final String finalContact = contact;

        workers.submit(() -> {
            try {
                api.createExchangeItem(finalTitle, finalDesc, finalCategory, finalType, finalContact);
                Platform.runLater(() -> {
                    if (onSuccess != null) onSuccess.run();
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSubmit.setDisable(false);
                    btnCancel.setDisable(false);
                    showError(ex.getMessage() != null ? ex.getMessage() : "No se pudo publicar el material.");
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
