package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import java.util.function.Consumer;

public final class CommunityOverviewCardController {

    @FXML private Label nameLabel;
    @FXML private Label categoryBadge;
    @FXML private Label visibilityBadge;
    @FXML private Label membershipBadge;
    @FXML private Label slugLabel;
    @FXML private Label membersLabel;
    @FXML private Label descLabel;
    @FXML private Label roleLabel;
    @FXML private Button btnDetails;

    private ApiClient.CommunityOverview item;
    private Consumer<ApiClient.CommunityOverview> onSelected;

    public void set(ApiClient.CommunityOverview ov) {
        set(ov, null);
    }

    public void set(ApiClient.CommunityOverview ov, Consumer<ApiClient.CommunityOverview> onSelected) {
        this.item = ov;
        this.onSelected = onSelected;

        nameLabel.setText(ov.name());
        categoryBadge.setText(ov.category() != null ? ov.category() : "ACADEMICA");
        visibilityBadge.setText(ov.visibility() != null ? ov.visibility() : "PUBLIC");
        slugLabel.setText(ov.slug());
        descLabel.setText(ov.description() != null && !ov.description().isBlank()
                ? ov.description() : "Sin descripción pública proporcionada.");
        membersLabel.setText("👥 " + ov.memberCount() + " miembro(s)");

        if (ov.isMember()) {
            membershipBadge.setVisible(true);
            membershipBadge.setText("✓ Miembro");
            roleLabel.setText(ov.userRole() != null ? "Rol: " + ov.userRole() : "");
        } else {
            membershipBadge.setVisible(false);
            roleLabel.setText("Espacio Abierto");
        }
    }

    @FXML
    private void onDetails() {
        if (onSelected != null && item != null) {
            onSelected.accept(item);
        } else if (item != null) {
            UiHelper.alert("Detalles del Espacio",
                    "Comunidad: " + item.name() + "\nIdentificador: " + item.slug() +
                            "\nCategoría: " + item.category() + "\nVisibilidad: " + item.visibility() +
                            "\nMiembros: " + item.memberCount() +
                            "\n\nDescripción:\n" + item.description(),
                    javafx.scene.control.Alert.AlertType.INFORMATION);
        }
    }
}
