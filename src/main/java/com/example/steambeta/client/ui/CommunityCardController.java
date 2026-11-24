package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import java.util.function.Consumer;

/**
 * Controlador de Tarjeta de Comunidad (FXML) — SteamBeta Campus.
 */
public final class CommunityCardController {

    @FXML private Label nameLabel;
    @FXML private Label roleBadge;
    @FXML private Label fullBadge;
    @FXML private Label descLabel;
    @FXML private Label capacityLabel;
    @FXML private Label slotsLabel;
    @FXML private ProgressBar capacityBar;
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

        int max = community.maxMembers() > 0 ? community.maxMembers() : 50;
        long count = Math.max(0, community.memberCount());
        int available = Math.max(0, community.availableSlots());
        boolean isFull = count >= max || available == 0;
        int pct = (int) Math.min(100, Math.round((count * 100.0) / max));

        if (capacityLabel != null) {
            capacityLabel.setText(count + " / " + max + " integrantes (" + pct + "%)");
        }
        if (slotsLabel != null) {
            slotsLabel.setText(isFull ? "Sin espacios disponibles" : available + " espacio(s) disponible(s)");
        }
        if (capacityBar != null) {
            capacityBar.setProgress(Math.min(1.0, count / (double) max));
        }
        if (fullBadge != null) {
            fullBadge.setVisible(isFull);
            fullBadge.setManaged(isFull);
        }
    }

    @FXML
    private void onEnter() {
        if (onEnter != null && community != null) {
            onEnter.accept(community);
        }
    }
}
