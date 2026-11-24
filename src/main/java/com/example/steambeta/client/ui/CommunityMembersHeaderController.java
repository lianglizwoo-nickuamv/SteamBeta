package com.example.steambeta.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controlador de cabecera de la pestaña de integrantes (FXML).
 */
public final class CommunityMembersHeaderController {

    @FXML
    private Label countLabel;
    @FXML
    private Button inviteBtn;

    private Runnable onInviteAction;

    public void set(int count, boolean isAdmin, Runnable onInvite) {
        set(count, 50, isAdmin, onInvite);
    }

    public void set(int count, int maxMembers, boolean isAdmin, Runnable onInvite) {
        this.onInviteAction = onInvite;
        int max = maxMembers > 0 ? maxMembers : 50;
        int available = Math.max(0, max - count);
        String status = available == 0 ? "Comunidad llena" : available + " espacio(s) disponible(s)";
        countLabel.setText("Integrantes (" + count + " / " + max + " · " + status + ")");
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
