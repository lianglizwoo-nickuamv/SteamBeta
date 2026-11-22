package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;

/**
 * Controlador de Tarjeta de Oportunidad (FXML) — SteamBeta Campus.
 */
public final class OpportunityCardController {

    @FXML private Label catBadge;
    @FXML private Label instBadge;
    @FXML private Label dueLabel;
    @FXML private Label titleLabel;
    @FXML private Label descLabel;
    @FXML private HBox linkRow;
    @FXML private Button copyBtn;

    private String targetUrl;

    public void set(ApiClient.OpportunityItem item) {
        catBadge.setText(item.category() != null ? item.category() : "OPORTUNIDAD");
        instBadge.setText(item.sourceInstitution() != null ? item.sourceInstitution() : "General");
        dueLabel.setText(item.deadline() != null ? "Vence: " + UiHelper.formatDateShort(item.deadline()) : "");
        titleLabel.setText(item.title());
        descLabel.setText(item.description() != null ? item.description() : "");

        this.targetUrl = item.sourceUrl();
        if (targetUrl != null && !targetUrl.isBlank()) {
            linkRow.setVisible(true);
            linkRow.setManaged(true);
        } else {
            linkRow.setVisible(false);
            linkRow.setManaged(false);
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
}
