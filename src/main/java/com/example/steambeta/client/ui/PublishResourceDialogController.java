package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Controlador del diálogo modal para publicar recursos académicos en una comunidad (V8).
 */
public final class PublishResourceDialogController {

    @FXML private Label communitySubtitle;
    @FXML private TextField titleField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField subjectField;
    @FXML private TextField urlField;
    @FXML private TextArea descriptionArea;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private ApiClient api;
    private ExecutorService workers;
    private ApiClient.Community community;
    private Consumer<ApiClient.ResourceItem> onSuccess;

    private static final Map<String, String> TYPE_LABELS_TO_ENUM = Map.of(
            "Enlace externo", "EXTERNAL_LINK",
            "Documento", "DOCUMENT",
            "PDF", "PDF",
            "Presentación / Diapositivas", "SLIDES",
            "Guía de laboratorio", "LAB_GUIDE"
    );

    @FXML
    public void initialize() {
        typeCombo.setItems(FXCollections.observableArrayList(
                "Enlace externo", "Documento", "PDF", "Presentación / Diapositivas", "Guía de laboratorio"
        ));
        typeCombo.setValue("Enlace externo");
    }

    public void init(ApiClient api, ExecutorService workers, ApiClient.Community community,
                     Consumer<ApiClient.ResourceItem> onSuccess) {
        this.api = api;
        this.workers = workers;
        this.community = community;
        this.onSuccess = onSuccess;
        if (community != null) {
            communitySubtitle.setText("Comunidad: " + community.name());
        }
    }

    @FXML
    private void onSubmit() {
        if (community == null) {
            showError("No hay una comunidad asociada.");
            return;
        }
        String title = titleField.getText() == null ? "" : titleField.getText().strip();
        if (title.length() < 3 || title.length() > 160) {
            showError("El título debe tener entre 3 y 160 caracteres.");
            return;
        }

        String desc = descriptionArea.getText() == null ? "" : descriptionArea.getText().strip();
        if (desc.length() > 2000) {
            showError("La descripción no puede superar 2000 caracteres.");
            return;
        }

        String subject = subjectField.getText() == null ? "" : subjectField.getText().strip();
        if (!subject.isBlank() && (subject.length() < 2 || subject.length() > 80)) {
            showError("La asignatura debe tener entre 2 y 80 caracteres.");
            return;
        }

        String typeLabel = typeCombo.getValue() != null ? typeCombo.getValue() : "Enlace externo";
        String typeEnum = TYPE_LABELS_TO_ENUM.getOrDefault(typeLabel, "EXTERNAL_LINK");

        String url = urlField.getText() == null ? "" : urlField.getText().strip();
        if ("EXTERNAL_LINK".equals(typeEnum) && url.isBlank()) {
            showError("Para un enlace externo debes indicar una URL válida (http:// o https://).");
            return;
        }
        if (!url.isBlank() && !url.matches("^https?://[^\\s/$.?#].[^\\s]*$")) {
            showError("La URL debe comenzar con http:// o https:// y tener un formato válido.");
            return;
        }

        btnSubmit.setDisable(true);
        btnCancel.setDisable(true);
        showInfo("Publicando recurso en la comunidad…");

        final String finalDesc = desc.isBlank() ? null : desc;
        final String finalUrl = url.isBlank() ? null : url;
        final String finalSubject = subject.isBlank() ? null : subject;

        workers.submit(() -> {
            try {
                var item = api.publishResource(community.id(), title, finalDesc, typeEnum, finalUrl, null, null, null, finalSubject);
                Platform.runLater(() -> {
                    if (onSuccess != null) onSuccess.accept(item);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSubmit.setDisable(false);
                    btnCancel.setDisable(false);
                    String cause = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "Error del servidor al publicar el recurso.";
                    showError("No se pudo publicar el recurso: " + cause);
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
