package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Controlador del diálogo de publicación/edición de material de intercambio con adjuntos (FXML).
 * Etiquetas en español mapeadas a enums persistentes V11/V15.
 */
public final class PublishMaterialDialogController {

    private static final long MAX_FILE_BYTES = 26_214_400L; // 25 MB
    private static final int MAX_FILES = 5;
    private static final Set<String> ALLOWED_EXTS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx",
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff"
    );

    private static final Map<String, String> CAT_TO_ENUM = Map.of(
            "Libros", "BOOK",
            "Calculadoras", "CALCULATOR",
            "Equipo de laboratorio", "LAB_EQUIPMENT",
            "Apuntes", "NOTES",
            "Herramientas", "TOOL",
            "Otros materiales", "OTHER"
    );

    private static final Map<String, String> TYPE_TO_ENUM = Map.of(
            "Intercambio", "EXCHANGE",
            "Donación", "GIFT",
            "Préstamo", "LOAN"
    );

    @FXML private Label headerTitle;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> typeCombo;
    @FXML private Label modalityHintLabel;
    @FXML private TextField contactField;
    @FXML private ListView<String> attachmentsList;
    @FXML private Button btnAttach;
    @FXML private Button btnRemoveFile;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private ApiClient api;
    private ExecutorService workers;
    private Runnable onSuccess;
    private ApiClient.ExchangeItem editingItem;
    private final List<ApiClient.ExchangeAttachment> storedAttachments = new ArrayList<>();
    private final List<Path> selectedFiles = new ArrayList<>();

    @FXML
    public void initialize() {
        categoryCombo.setItems(FXCollections.observableArrayList(
                "Libros", "Apuntes", "Herramientas", "Calculadoras", "Equipo de laboratorio", "Otros materiales"
        ));
        categoryCombo.setValue("Libros");

        typeCombo.setItems(FXCollections.observableArrayList(
                "Intercambio", "Donación", "Préstamo"
        ));
        typeCombo.setValue("Intercambio");
        typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateModalityHint(newVal));
        updateModalityHint("Intercambio");
    }

    private void updateModalityHint(String selectedLabel) {
        if (modalityHintLabel == null) return;
        modalityHintLabel.setText(modalityExplanation(TYPE_TO_ENUM.getOrDefault(selectedLabel, "EXCHANGE")));
    }

    public static String modalityExplanation(String code) {
        if (code == null) code = "EXCHANGE";
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "GIFT" -> "Donación: entrega gratuita y permanente de un material. No se espera devolución ni pago.";
            case "LOAN" -> "Préstamo: entrega temporal de un material que debe devolverse. Actualmente no existe una fecha estructurada de devolución ni seguimiento formal del préstamo; acuerda las condiciones directamente.";
            default -> "Intercambio: trueque de un material por otro sin dinero. Como no existe un campo estructurado para el material solicitado, detalla en la descripción qué material buscas a cambio.";
        };
    }

    public void init(ApiClient api, ExecutorService workers, Runnable onSuccess) {
        this.api = api;
        this.workers = workers;
        this.onSuccess = onSuccess;
        this.editingItem = null;
    }

    public void initForEdit(ApiClient api, ExecutorService workers, ApiClient.ExchangeItem item, Runnable onSuccess) {
        this.api = api;
        this.workers = workers;
        this.onSuccess = onSuccess;
        this.editingItem = item;
        if (item != null) {
            if (headerTitle != null) headerTitle.setText("Editar Material de Intercambio");
            titleField.setText(item.title() != null ? item.title() : "");
            descriptionArea.setText(item.description() != null ? item.description() : "");
            contactField.setText(item.contactInfo() != null ? item.contactInfo() : "");
            categoryCombo.setValue(categoryLabel(item.category()));
            typeCombo.setValue(typeLabel(item.exchangeType()));
            btnSubmit.setText("Guardar Cambios");
            loadExistingAttachments(item.id());
        }
    }

    private void loadExistingAttachments(String itemId) {
        if (api == null || workers == null || itemId == null) return;
        workers.submit(() -> {
            try {
                var list = api.exchangeAttachments(itemId);
                Platform.runLater(() -> {
                    storedAttachments.clear();
                    if (list != null) storedAttachments.addAll(list);
                    refreshAttachmentsListView();
                });
            } catch (Exception ignored) {}
        });
    }

    private void refreshAttachmentsListView() {
        attachmentsList.getItems().clear();
        for (var att : storedAttachments) {
            attachmentsList.getItems().add("[Almacenado] " + att.originalFilename() + " (."
                    + att.extension().toUpperCase(Locale.ROOT) + " · " + formatSize(att.fileSizeBytes()) + ")");
        }
        for (Path p : selectedFiles) {
            String name = p.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "";
            long size = 0;
            try { size = Files.size(p); } catch (Exception ignored) {}
            attachmentsList.getItems().add("[Nuevo] " + name + " (." + ext + " · " + formatSize(size) + ")");
        }
    }

    public static String categoryLabel(String code) {
        if (code == null) return "Otros materiales";
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "BOOK" -> "Libros";
            case "CALCULATOR" -> "Calculadoras";
            case "LAB_EQUIPMENT" -> "Equipo de laboratorio";
            case "NOTES" -> "Apuntes";
            case "TOOL" -> "Herramientas";
            default -> "Otros materiales";
        };
    }

    public static String typeLabel(String code) {
        if (code == null) return "Intercambio";
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "GIFT" -> "Donación";
            case "LOAN" -> "Préstamo";
            default -> "Intercambio";
        };
    }

    @FXML
    private void onAttachFiles() {
        Stage owner = titleField.getScene() != null ? (Stage) titleField.getScene().getWindow() : null;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar archivos adjuntos (máx. 25 MB)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Documentos, presentaciones e imágenes",
                        "*.pdf", "*.doc", "*.docx", "*.ppt", "*.pptx",
                        "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp", "*.bmp", "*.tif", "*.tiff"),
                new FileChooser.ExtensionFilter("Documentos (PDF, DOC, DOCX)", "*.pdf", "*.doc", "*.docx"),
                new FileChooser.ExtensionFilter("Presentaciones (PPT, PPTX)", "*.ppt", "*.pptx"),
                new FileChooser.ExtensionFilter("Imágenes", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp", "*.bmp", "*.tif", "*.tiff")
        );
        List<File> chosen = chooser.showOpenMultipleDialog(owner);
        if (chosen == null || chosen.isEmpty()) return;

        for (File f : chosen) {
            if (storedAttachments.size() + selectedFiles.size() >= MAX_FILES) {
                showError("Solo puedes tener hasta " + MAX_FILES + " archivos por publicación.");
                break;
            }
            Path p = f.toPath().toAbsolutePath().normalize();
            if (selectedFiles.contains(p)) {
                showInfo("El archivo '" + p.getFileName() + "' ya está seleccionado.");
                continue;
            }
            String name = p.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
            if (!ALLOWED_EXTS.contains(ext)) {
                showError("Formato no admitido: " + name);
                continue;
            }
            try {
                long size = Files.size(p);
                if (size <= 0 || size > MAX_FILE_BYTES) {
                    showError("El archivo '" + name + "' supera el límite de 25 MB o está vacío.");
                    continue;
                }
                selectedFiles.add(p);
                refreshAttachmentsListView();
                showInfo("Archivo seleccionado (se subirá al guardar): " + name);
            } catch (Exception ex) {
                showError("No se pudo leer el archivo '" + name + "'.");
            }
        }
    }

    @FXML
    private void onRemoveSelectedFile() {
        int idx = attachmentsList.getSelectionModel().getSelectedIndex();
        if (idx < 0) return;
        if (idx < storedAttachments.size()) {
            showInfo("Los archivos ya almacenados se eliminan desde 'Ver detalle / Adjuntos' para evitar borrados accidentales.");
            return;
        }
        int newIdx = idx - storedAttachments.size();
        if (newIdx >= 0 && newIdx < selectedFiles.size()) {
            selectedFiles.remove(newIdx);
            refreshAttachmentsListView();
        }
    }

    @FXML
    private void onSubmit() {
        String title = titleField.getText() == null ? "" : titleField.getText().strip();
        if (title.length() < 3 || title.length() > 160) {
            showError("El título debe tener entre 3 y 160 caracteres.");
            return;
        }

        String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().strip();
        if (description.length() < 5 || description.length() > 3000) {
            showError("La descripción debe tener entre 5 y 3000 caracteres.");
            return;
        }

        String category = CAT_TO_ENUM.getOrDefault(categoryCombo.getValue(), "BOOK");
        String type = TYPE_TO_ENUM.getOrDefault(typeCombo.getValue(), "EXCHANGE");

        String contact = contactField.getText() == null ? "" : contactField.getText().strip();
        if (contact.length() < 3 || contact.length() > 300) {
            showError("Indica cómo contactarte (entre 3 y 300 caracteres).");
            return;
        }

        setBusy(true);
        showInfo(editingItem != null ? "Guardando cambios…" : "Publicando material y subiendo adjuntos…");

        final List<Path> filesToUpload = List.copyOf(selectedFiles);

        workers.submit(() -> {
            try {
                ApiClient.ExchangeItem saved;
                if (editingItem != null) {
                    saved = api.updateExchangeItem(editingItem.id(), title, description, category, type, null, contact);
                } else {
                    saved = api.createExchangeItem(title, description, category, type, contact);
                }

                for (int i = 0; i < filesToUpload.size(); i++) {
                    final int step = i + 1;
                    final Path file = filesToUpload.get(i);
                    Platform.runLater(() -> showInfo("Subiendo archivo " + step + " de " + filesToUpload.size() + ": " + file.getFileName()));
                    api.uploadExchangeAttachment(saved.id(), file);
                }

                Platform.runLater(() -> {
                    if (onSuccess != null) onSuccess.run();
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setBusy(false);
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "No se pudo guardar el material.";
                    showError(msg);
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        btnSubmit.setDisable(busy);
        btnCancel.setDisable(busy);
        if (btnAttach != null) btnAttach.setDisable(busy);
        if (btnRemoveFile != null) btnRemoveFile.setDisable(busy);
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

    public static String formatSize(long bytes) {
        return UiHelper.formatFileSize(bytes);
    }
}
