package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class CommunityCreatedSuccessDialogController {

    @FXML private Label nameLabel;
    @FXML private Label categoryBadge;
    @FXML private Label visibilityBadge;
    @FXML private Label roleLabel;
    @FXML private VBox invitationBlock;
    @FXML private TextField codeField;
    @FXML private Button btnCopy;

    public void set(ApiClient.Community community) {
        if (community == null) return;

        nameLabel.setText(community.name() != null ? community.name() : "Comunidad");
        roleLabel.setText("Tu rol asignado: Administrador Local (ADMIN)");

        if (community.initialInvitationCode() != null && !community.initialInvitationCode().isBlank()) {
            codeField.setText(community.initialInvitationCode());
            invitationBlock.setVisible(true);
            invitationBlock.setManaged(true);
        } else {
            invitationBlock.setVisible(false);
            invitationBlock.setManaged(false);
        }
    }

    public void setDirect(String communityName, String code) {
        nameLabel.setText(communityName != null ? communityName : "Comunidad");
        roleLabel.setText("Código de Invitación Generado:");
        codeField.setText(code != null ? code : "");
        invitationBlock.setVisible(true);
        invitationBlock.setManaged(true);
    }

    @FXML
    private void onCopyCode() {
        String code = codeField.getText();
        if (code != null && !code.isBlank()) {
            var cb = Clipboard.getSystemClipboard();
            var cc = new ClipboardContent();
            cc.putString(code);
            cb.setContent(cc);
            btnCopy.setText("✓ ¡Copiado!");
            btnCopy.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold;");
        }
    }

    @FXML
    private void onClose() {
        if (nameLabel.getScene() != null && nameLabel.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }
}
