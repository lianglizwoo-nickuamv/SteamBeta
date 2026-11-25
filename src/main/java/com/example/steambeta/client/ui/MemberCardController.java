package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import java.util.concurrent.ExecutorService;

/**
 * Controlador para tarjeta de integrante de comunidad (FXML).
 */
public final class MemberCardController {

    @FXML private ImageView avatarImage;
    @FXML private Label avatarInitials;
    @FXML private Label aliasLabel;
    @FXML private Label roleBadge;
    @FXML private Label universityLabel;

    public void set(ApiClient.Member m) {
        set(m, null, null);
    }

    public void set(ApiClient.Member m, ApiClient api, ExecutorService workers) {
        aliasLabel.setText(m.alias() != null ? m.alias() : "Estudiante");
        AvatarLoader.load(avatarImage, avatarInitials, m.alias(), m.accountId(), m.avatarUrl(), 36.0, api, workers);
        roleBadge.setText(m.role() != null ? m.role() : "MEMBER");
        roleBadge.getStyleClass().removeAll("badge-admin", "badge-role");
        if ("ADMIN".equals(m.role())) {
            roleBadge.getStyleClass().add("badge-admin");
        } else {
            roleBadge.getStyleClass().add("badge-role");
        }
        universityLabel.setText(m.declaredUniversity() != null ? m.declaredUniversity() : "");
    }
}
