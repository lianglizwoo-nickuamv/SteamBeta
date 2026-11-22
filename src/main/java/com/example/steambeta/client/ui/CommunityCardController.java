package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import java.util.function.Consumer;

/**
 * Controlador de Tarjeta de Comunidad (FXML) — SteamBeta Campus.
 */
public final class CommunityCardController {

    @FXML private Label nameLabel;
    @FXML private Label roleBadge;
    @FXML private Label descLabel;
    @FXML private Button enterBtn;

    private ApiClient.Community community;
    private Consumer<ApiClient.Community> onEnter;

    public void set(ApiClient.Community community, Consumer<ApiClient.Community> onEnter) {
        this.community = community;
        this.onEnter = onEnter;

        nameLabel.setText(community.name());
        descLabel.setText(community.description() != null ? community.description() : "");

        String role = community.role() != null ? community.role() : "MEMBER";
        roleBadge.setText(role);
        roleBadge.getStyleClass().removeAll("badge-admin", "badge-mod", "badge-role");
        if ("ADMIN".equals(role)) {
            roleBadge.getStyleClass().add("badge-admin");
        } else if ("MODERATOR".equals(role)) {
            roleBadge.getStyleClass().add("badge-mod");
        } else {
            roleBadge.getStyleClass().add("badge-role");
        }
    }

    @FXML
    private void onEnter() {
        if (onEnter != null && community != null) {
            onEnter.accept(community);
        }
    }
}
