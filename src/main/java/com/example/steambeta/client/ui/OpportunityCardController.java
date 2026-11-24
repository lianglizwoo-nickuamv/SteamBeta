package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Controlador de Tarjeta de Oportunidad (FXML) — SteamBeta Campus.
 */
public final class OpportunityCardController {

    @FXML
    private Label catBadge;
    @FXML
    private Label instBadge;
    @FXML
    private Label dueLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private Label descLabel;
    @FXML
    private HBox linkRow;
    @FXML
    private Button openBtn;
    @FXML
    private Button copyBtn;
    @FXML
    private Button btnEdit;
    @FXML
    private Button btnDelete;

    private String targetUrl;
    private Runnable onEditAction;
    private Runnable onDeleteAction;

    public void set(ApiClient.OpportunityItem item) {
        set(item, false, null, null);
    }

    public void set(ApiClient.OpportunityItem item, boolean canManage, Runnable onEdit, Runnable onDelete) {
        this.onEditAction = onEdit;
        this.onDeleteAction = onDelete;

        catBadge.setText(PublishOpportunityDialogController.categoryLabel(item.category()));
        instBadge.setText(item.sourceInstitution() != null ? item.sourceInstitution() : "Institución");
        dueLabel.setText(
                item.deadline() != null ? "Cierra: " + UiHelper.formatDateShort(item.deadline()) : "Sin fecha límite");
        titleLabel.setText(item.title());
        descLabel.setText(item.description() != null ? item.description() : "");

        this.targetUrl = item.sourceUrl();
        boolean hasLink = targetUrl != null && !targetUrl.isBlank();
        linkRow.setVisible(hasLink);
        linkRow.setManaged(hasLink);

        if (btnEdit != null) {
            btnEdit.setVisible(canManage && onEdit != null);
            btnEdit.setManaged(canManage && onEdit != null);
        }
        if (btnDelete != null) {
            btnDelete.setVisible(canManage && onDelete != null);
            btnDelete.setManaged(canManage && onDelete != null);
        }
    }

    @FXML
    private void onOpenLink() {
        if (targetUrl == null || targetUrl.isBlank())
            return;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", targetUrl).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", targetUrl).start();
            } else {
                new ProcessBuilder("xdg-open", targetUrl).start();
            }
        } catch (Exception ex) {
            Stage owner = titleLabel.getScene() != null ? (Stage) titleLabel.getScene().getWindow() : null;
            UiHelper.showError(owner, "No se pudo abrir el enlace",
                    "Puedes copiar el enlace con el botón 'Copiar enlace'.");
        }
    }

    @FXML
    private void onCopyLink() {
        if (targetUrl != null && !targetUrl.isBlank()) {
            var cb = Clipboard.getSystemClipboard();
            var content = new ClipboardContent();
            content.putString(targetUrl);
            cb.setContent(content);
            copyBtn.setText("✓ Copiado");
        }
    }

    @FXML
    private void onEdit() {
        if (onEditAction != null)
            onEditAction.run();
    }

    @FXML
    private void onDelete() {
        if (onDeleteAction != null)
            onDeleteAction.run();
    }
}
