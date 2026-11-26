package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Controlador del diálogo modal para publicar recursos académicos en una comunidad (V8).
 * Soporta enlaces externos y subida real de archivos descargables (máx. 25 MB).
 */
public final class PublishResourceDialogController {

    private static final long MAX_FILE_SIZE = 26_214_400L; // 25 MB (V8)

    @FXML private Label communitySubtitle;
    @FXML private TextField titleField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField subjectField;
    @FXML private VBox urlBox;
    @FXML private TextField urlField;
    @FXML private VBox fileBox;
    @FXML private Button btnChooseFile;
    @FXML private Button btnRemoveFile;
    @FXML private Label fileInfoLabel;
    @FXML private TextArea descriptionArea;
    @FXML private CheckBox rightsCheckBox;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private ApiClient api;
    private ExecutorService workers;
    private ApiClient.Community community;
    private Consumer<ApiClient.ResourceItem> onSuccess;
    private Path selectedFile;

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
                "Enlace externo", "PDF", "Documento", "Presentación / Diapositivas", "Guía de laboratorio"
        ));
        typeCombo.setValue("Enlace externo");
        typeCombo.valueProperty().addListener((obs, oldV, newV) -> updateInputVisibility());
        updateInputVisibility();
    }

    public void init(ApiClient api, ExecutorService workers, ApiClient.Community community,
                     Consumer<ApiClient.ResourceItem> onSuccess) {
        this.api = api;
        this.workers = workers;
        this.community = community;
        this.onSuccess = onSuccess;
        if (community != null) {
            communitySubtitle.setText("Comunidad: " + community.name() + " · Los archivos pertenecen exclusivamente a esta comunidad.");
        }
    }

    private boolean isExternalLink() {
        return "EXTERNAL_LINK".equals(currentTypeEnum());
    }

    private String currentTypeEnum() {
        String label = typeCombo.getValue() != null ? typeCombo.getValue() : "Enlace externo";
        return TYPE_LABELS_TO_ENUM.getOrDefault(label, "EXTERNAL_LINK");
    }

    private void updateInputVisibility() {
        boolean link = isExternalLink();
        UiHelper.setVisible(urlBox, link);
        UiHelper.setVisible(fileBox, !link);
        if (link) {
            selectedFile = null;
            updateFileInfo();
        }
    }

    private void updateFileInfo() {
        if (selectedFile == null) {
            fileInfoLabel.setText("Ningún archivo seleccionado.");
            UiHelper.setVisible(btnRemoveFile, false);
            btnChooseFile.setText("Seleccionar archivo");
        } else {
            File f = selectedFile.toFile();
            fileInfoLabel.setText(f.getName() + " (" + UiHelper.formatFileSize(f.length()) + ")");
            UiHelper.setVisible(btnRemoveFile, true);
            btnChooseFile.setText("Reemplazar archivo");
        }
    }

    @FXML
    private void onChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar recurso académico (máx. 25 MB)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Documentos e imágenes permitidos",
                "*.pdf", "*.doc", "*.docx", "*.ppt", "*.pptx",
                "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp", "*.bmp", "*.tif", "*.tiff"
        ));
        Stage owner = titleField.getScene() != null ? (Stage) titleField.getScene().getWindow() : null;
        File chosen = chooser.showOpenDialog(owner);
        if (chosen == null) return;

        if (chosen.length() <= 0 || chosen.length() > MAX_FILE_SIZE) {
            showError("El archivo seleccionado debe ser mayor a 0 bytes y no superar 25 MB.");
            return;
        }
        selectedFile = chosen.toPath();
        updateFileInfo();
        UiHelper.setVisible(errorLabel, false);
    }

    @FXML
    private void onRemoveFile() {
        selectedFile = null;
        updateFileInfo();
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

        if (!rightsCheckBox.isSelected()) {
            showError("Debes confirmar que tienes derecho a compartir este material (propio, dominio público o con licencia).");
            return;
        }

        String typeEnum = currentTypeEnum();
        String url = urlField.getText() == null ? "" : urlField.getText().strip();

        if ("EXTERNAL_LINK".equals(typeEnum)) {
            if (url.isBlank() || !url.matches("^https?://[^\\s/$.?#].[^\\s]*$")) {
                showError("Para un enlace externo debes indicar una URL válida que comience con http:// o https://.");
                return;
            }
        } else {
            if (selectedFile == null) {
                showError("Selecciona un archivo local (máx. 25 MB) para publicar este tipo de recurso.");
                return;
            }
            if (selectedFile.toFile().length() > MAX_FILE_SIZE) {
                showError("El archivo supera el límite máximo permitido de 25 MB.");
                return;
            }
        }

        btnSubmit.setDisable(true);
        btnCancel.setDisable(true);
        showInfo("EXTERNAL_LINK".equals(typeEnum)
                ? "Publicando enlace en la comunidad…"
                : "Subiendo y validando archivo en la comunidad…");

        final String finalDesc = desc.isBlank() ? null : desc;
        final String finalUrl = url.isBlank() ? null : url;
        final String finalSubject = subject.isBlank() ? null : subject;
        final Path fileToUpload = selectedFile;

        workers.submit(() -> {
            try {
                ApiClient.ResourceItem item = "EXTERNAL_LINK".equals(typeEnum)
                        ? api.publishResource(community.id(), title, finalDesc, typeEnum, finalUrl, null, null, null, finalSubject)
                        : api.publishResourceWithFile(community.id(), title, finalDesc, typeEnum, finalSubject, fileToUpload);
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
