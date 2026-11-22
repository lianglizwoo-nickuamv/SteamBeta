package com.example.steambeta.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controlador de cabecera de la pestaña de integrantes (FXML).
 */
public final class CommunityMembersHeaderController {

    @FXML private Label countLabel;
    @FXML private Button inviteBtn;

    private Runnable onInviteAction;

    public void set(int count, boolean isAdmin, Runnable onInvite) {
        this.onInviteAction = onInvite;
        countLabel.setText("Integrantes (" + count + ")");
        inviteBtn.setVisible(isAdmin);
        inviteBtn.setManaged(isAdmin);
    }

    @FXML
    private void onInvite() {
        if (onInviteAction != null) {
            onInviteAction.run();
        }
    }
}
