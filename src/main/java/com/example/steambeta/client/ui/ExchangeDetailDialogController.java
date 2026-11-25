package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Diálogo modal de detalle de material de intercambio con lista de adjuntos,
 * vista previa de imágenes compatibles y descarga autorizada del archivo original.
 */
public final class ExchangeDetailDialogController {

    private static final Set<String> PREVIEWABLE_IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "bmp");

    @FXML private Label typeBadge;
    @FXML private Label catBadge;
    @FXML private Label ownerLabel;
    @FXML private Label titleLabel;
    @FXML private Label descLabel;
    @FXML private Label contactLabel;
    @FXML private ListView<String> attachmentsList;
    @FXML private Button btnDownload;
    @FXML private Button btnDeleteAtt;
    @FXML private VBox previewBox;
    @FXML private ImageView previewImage;
    @FXML private Label statusLabel;

    private ApiClient api;
    private ExecutorService workers;
    private ApiClient.ExchangeItem item;
    private boolean isOwner;
    private Runnable onChanged;
    private final List<ApiClient.ExchangeAttachment> attachments = new ArrayList<>();

    public void init(ApiClient api, ExecutorService workers, ApiClient.ExchangeItem item,
                     boolean isOwner, Runnable onChanged) {
        this.api = api;
        this.workers = workers;
        this.item = item;
        this.isOwner = isOwner;
        this.onChanged = onChanged;

        typeBadge.setText(PublishMaterialDialogController.typeLabel(item.exchangeType()));
        catBadge.setText(PublishMaterialDialogController.categoryLabel(item.category()));
        String author = item.ownerAlias() != null && !item.ownerAlias().isBlank() ? item.ownerAlias() : "Estudiante";
        String dateText = item.createdAt() != null && !item.createdAt().isBlank()
                ? " · " + UiHelper.formatDate(item.createdAt()) : "";
        String statusText = "AVAILABLE".equalsIgnoreCase(item.status()) ? " · Disponible" : "";
        ownerLabel.setText("Publicado por " + author + dateText + statusText);
        titleLabel.setText(item.title());
        String modalityNote = PublishMaterialDialogController.modalityExplanation(item.exchangeType());
        descLabel.setText((item.description() != null ? item.description() : "") + "\n\nCondición: " + modalityNote);
        contactLabel.setText("Contacto: " + (item.contactInfo() != null ? item.contactInfo() : "—"));

        btnDeleteAtt.setVisible(isOwner);
        btnDeleteAtt.setManaged(isOwner);

        attachmentsList.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            int idx = newIdx != null ? newIdx.intValue() : -1;
            boolean valid = idx >= 0 && idx < attachments.size();
            btnDownload.setDisable(!valid);
            btnDeleteAtt.setDisable(!valid);
            if (valid) {
                loadPreviewIfImage(attachments.get(idx));
            } else {
                hidePreview();
            }
        });

        loadAttachments();
    }

    private void loadAttachments() {
        statusLabel.setText("Cargando archivos adjuntos...");
        workers.submit(() -> {
            try {
                var list = api.exchangeAttachments(item.id());
                Platform.runLater(() -> {
                    attachments.clear();
                    attachments.addAll(list);
                    attachmentsList.getItems().clear();
                    for (var att : attachments) {
                        attachmentsList.getItems().add(att.originalFilename() + " (."
                                + att.extension().toUpperCase() + " · "
                                + PublishMaterialDialogController.formatSize(att.fileSizeBytes()) + ")");
                    }
                    if (attachments.isEmpty()) {
                        statusLabel.setText("Esta publicación no tiene archivos adjuntos.");
                    } else {
                        statusLabel.setText(attachments.size() + " archivo(s) disponible(s). Selecciona uno para previsualizar o descargar.");
                        attachmentsList.getSelectionModel().selectFirst();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("No se pudieron cargar los adjuntos: "
                        + (ex.getMessage() != null ? ex.getMessage() : "Error del servidor.")));
            }
        });
    }

    private void loadPreviewIfImage(ApiClient.ExchangeAttachment att) {
        if (att.extension() == null || !PREVIEWABLE_IMAGE_EXTS.contains(att.extension().toLowerCase())) {
            hidePreview();
            String ext = att.extension() != null ? "." + att.extension().toUpperCase() : "este formato";
            statusLabel.setText("Vista previa no disponible para " + ext + "; pulsa 'Descargar archivo' para abrir el archivo original.");
            return;
        }
        statusLabel.setText("Cargando vista previa de " + att.originalFilename() + "…");
        workers.submit(() -> {
            try {
                byte[] bytes = api.downloadExchangeAttachmentBytes(att.id());
                Image img = new Image(new ByteArrayInputStream(bytes));
                Platform.runLater(() -> {
                    if (!img.isError()) {
                        previewImage.setImage(img);
                        previewBox.setVisible(true);
                        previewBox.setManaged(true);
                        statusLabel.setText("Vista previa lista: " + att.originalFilename());
                    } else {
                        hidePreview();
                        statusLabel.setText("Vista previa no disponible para este formato; puedes descargar el archivo original.");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    hidePreview();
                    statusLabel.setText("No se pudo previsualizar: " + ex.getMessage());
                });
            }
        });
    }

    private void hidePreview() {
        previewImage.setImage(null);
        previewBox.setVisible(false);
        previewBox.setManaged(false);
    }

    @FXML
    private void onDownloadSelected() {
        int idx = attachmentsList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= attachments.size()) return;
        ApiClient.ExchangeAttachment att = attachments.get(idx);

        Stage owner = titleLabel.getScene() != null ? (Stage) titleLabel.getScene().getWindow() : null;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar archivo adjunto");
        chooser.setInitialFileName(att.originalFilename());
        File dest = chooser.showSaveDialog(owner);
        if (dest == null) return;

        btnDownload.setDisable(true);
        statusLabel.setText("Descargando " + att.originalFilename() + "…");
        workers.submit(() -> {
            try {
                api.downloadExchangeAttachmentTo(att.id(), dest.toPath());
                Platform.runLater(() -> {
                    btnDownload.setDisable(false);
                    statusLabel.setText("Archivo guardado en: " + dest.getName());
                    UiHelper.showSuccess(owner, "Descarga Completada",
                            "El archivo '" + att.originalFilename() + "' se guardó correctamente.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnDownload.setDisable(false);
                    statusLabel.setText("Error al descargar: " + ex.getMessage());
                    UiHelper.showError(owner, "Error de Descarga", ex.getMessage());
                });
            }
        });
    }

    @FXML
    private void onDeleteSelected() {
        if (!isOwner) return;
        int idx = attachmentsList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= attachments.size()) return;
        ApiClient.ExchangeAttachment att = attachments.get(idx);

        Stage owner = titleLabel.getScene() != null ? (Stage) titleLabel.getScene().getWindow() : null;
        if (!UiHelper.showConfirm(owner, "Eliminar adjunto", "¿Deseas eliminar el archivo '" + att.originalFilename() + "'?")) {
            return;
        }

        workers.submit(() -> {
            try {
                api.deleteExchangeAttachment(att.id());
                Platform.runLater(() -> {
                    hidePreview();
                    loadAttachments();
                    if (onChanged != null) onChanged.run();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> UiHelper.showError(owner, "Error", "No se pudo eliminar el adjunto: " + ex.getMessage()));
            }
        });
    }

    @FXML
    private void onClose() {
        if (titleLabel.getScene() != null && titleLabel.getScene().getWindow() instanceof Stage s) {
            s.close();
        }
    }
}
