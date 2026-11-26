package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Controlador para tarjeta de recurso académico comunitario (V8 · FXML).
 * Muestra metadatos reales y permite descargar archivos o abrir enlaces externos.
 */
public final class ResourceCardController {

    @FXML private Label typeBadge;
    @FXML private Label subjectBadge;
    @FXML private Label uploaderLabel;
    @FXML private Label dateLabel;
    @FXML private Label titleLabel;
    @FXML private Label descLabel;
    @FXML private Label attachmentMetaLabel;
    @FXML private Button btnDownload;
    @FXML private Button btnOpenLink;

    private ApiClient.ResourceItem resource;
    private ApiClient api;
    private ExecutorService workers;

    public void set(ApiClient.ResourceItem r) {
        set(r, null, null);
    }

    public void set(ApiClient.ResourceItem r, ApiClient api, ExecutorService workers) {
        this.resource = r;
        this.api = api;
        this.workers = workers;

        typeBadge.setText(formatType(r.resourceType()));
        if (r.subjectName() != null && !r.subjectName().isBlank()) {
            subjectBadge.setText(r.subjectName());
            UiHelper.setVisible(subjectBadge, true);
        } else {
            UiHelper.setVisible(subjectBadge, false);
        }
        uploaderLabel.setText("Por: " + (r.uploaderAlias() != null ? r.uploaderAlias() : "Estudiante"));
        dateLabel.setText(UiHelper.formatDate(r.createdAt()));
        titleLabel.setText(r.title());

        if (r.description() != null && !r.description().isBlank()) {
            descLabel.setText(r.description());
            UiHelper.setVisible(descLabel, true);
        } else {
            UiHelper.setVisible(descLabel, false);
        }

        boolean hasFile = r.originalFilename() != null && !r.originalFilename().isBlank();
        boolean hasLink = r.externalUrl() != null && !r.externalUrl().isBlank();

        if (hasFile) {
            String sizeText = r.fileSizeBytes() != null ? " · " + UiHelper.formatFileSize(r.fileSizeBytes()) : "";
            attachmentMetaLabel.setText("Documento descargable: " + r.originalFilename() + sizeText);
            UiHelper.setVisible(btnDownload, api != null && workers != null);
        } else if (hasLink) {
            attachmentMetaLabel.setText("Enlace externo: " + r.externalUrl());
            UiHelper.setVisible(btnDownload, false);
        } else {
            attachmentMetaLabel.setText("Recurso académico sin archivo adjunto.");
            UiHelper.setVisible(btnDownload, false);
        }
        UiHelper.setVisible(btnOpenLink, hasLink);
    }

    @FXML
    private void onDownload() {
        if (resource == null || api == null || workers == null) return;
        Stage owner = titleLabel.getScene() != null ? (Stage) titleLabel.getScene().getWindow() : null;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar recurso comunitario");
        chooser.setInitialFileName(resource.originalFilename() != null ? resource.originalFilename() : "recurso");
        File target = chooser.showSaveDialog(owner);
        if (target == null) return;

        btnDownload.setDisable(true);
        btnDownload.setText("Descargando…");
        workers.submit(() -> {
            try {
                byte[] bytes = api.downloadCommunityResource(resource.communityId(), resource.id());
                Files.write(target.toPath(), bytes);
                Platform.runLater(() -> {
                    btnDownload.setDisable(false);
                    btnDownload.setText("Descargar");
                    UiHelper.showSuccess(owner, "Recurso descargado", target.getAbsolutePath());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnDownload.setDisable(false);
                    btnDownload.setText("Descargar");
                    UiHelper.showError(owner, "Error al descargar", ex.getMessage());
                });
            }
        });
    }

    @FXML
    private void onOpenLink() {
        if (resource == null || resource.externalUrl() == null || resource.externalUrl().isBlank()) return;
        Stage owner = titleLabel.getScene() != null ? (Stage) titleLabel.getScene().getWindow() : null;
        try {
            URI uri = URI.create(resource.externalUrl().strip());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                UiHelper.showError(owner, "Enlace inválido", "Solo se permiten enlaces http:// o https://.");
                return;
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri.toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", uri.toString()).start();
            } else {
                new ProcessBuilder("xdg-open", uri.toString()).start();
            }
        } catch (Exception ex) {
            UiHelper.showError(owner, "No se pudo abrir el enlace", ex.getMessage());
        }
    }

    private static String formatType(String type) {
        if (type == null) return "RECURSO";
        return switch (type.toUpperCase()) {
            case "PDF" -> "PDF";
            case "DOCUMENT" -> "Documento";
            case "SLIDES" -> "Presentación";
            case "LAB_GUIDE" -> "Guía de laboratorio";
            case "EXTERNAL_LINK" -> "Enlace externo";
            default -> type;
        };
    }
}
