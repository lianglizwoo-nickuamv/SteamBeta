package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import java.util.function.Consumer;

/**
 * Tarjeta del catálogo global "Explorar Comunidades" (FXML) — SteamBeta Campus (V14).
 * Muestra ocupación real (X / Y integrantes, espacios disponibles y barra de ocupación)
 * y adapta su botón según privacidad, capacidad y membresía:
 * - Ya miembro -> "Entrar"
 * - Comunidad llena (no miembro) -> Etiqueta "Comunidad llena" + Botón deshabilitado
 * - Pública con espacio -> Etiqueta "Pública" + Botón "Unirse"
 * - Solo con invitación con espacio -> Etiqueta "Requiere invitación" + Botón "Ingresar código"
 */
public final class CommunityOverviewCardController {

    @FXML private Label nameLabel;
    @FXML private Label categoryBadge;
    @FXML private Label visibilityBadge;
    @FXML private Label fullBadge;
    @FXML private Label membershipBadge;
    @FXML private Label slugLabel;
    @FXML private Label membersLabel;
    @FXML private Label slotsLabel;
    @FXML private ProgressBar capacityBar;
    @FXML private Label descLabel;
    @FXML private Label roleLabel;
    @FXML private Button btnDetails;

    private ApiClient.CommunityOverview item;
    private Consumer<ApiClient.CommunityOverview> onAction;

    public void set(ApiClient.CommunityOverview ov) {
        set(ov, null);
    }

    public void set(ApiClient.CommunityOverview ov, Consumer<ApiClient.CommunityOverview> onAction) {
        this.item = ov;
        this.onAction = onAction;

        nameLabel.setText(ov.name());
        categoryBadge.setText(formatCategory(ov.category()));
        slugLabel.setText(ov.slug());
        descLabel.setText(ov.description() != null && !ov.description().isBlank()
                ? ov.description() : "Sin descripción pública proporcionada.");

        int max = ov.maxMembers() > 0 ? ov.maxMembers() : 50;
        long count = Math.max(0, ov.memberCount());
        int available = Math.max(0, ov.availableSlots());
        boolean isFull = count >= max || available == 0;
        int pct = (int) Math.min(100, Math.round((count * 100.0) / max));

        membersLabel.setText("👥 " + count + " / " + max + " integrantes (" + pct + "%)");
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

        boolean isInviteOnly = "INVITE_ONLY".equalsIgnoreCase(ov.visibility());
        visibilityBadge.getStyleClass().removeAll("badge-admin", "badge-pending", "badge-role");
        if (isInviteOnly) {
            visibilityBadge.setText("Requiere invitación");
            visibilityBadge.getStyleClass().add("badge-pending");
        } else {
            visibilityBadge.setText("Pública");
            visibilityBadge.getStyleClass().add("badge-admin");
        }

        btnDetails.getStyleClass().removeAll("primary", "secondary");
        btnDetails.setDisable(false);
        if (ov.isMember()) {
            membershipBadge.setVisible(true);
            membershipBadge.setManaged(true);
            membershipBadge.setText("✓ Miembro");
            roleLabel.setText("Tu rol: " + (ov.userRole() != null ? ov.userRole() : "MEMBER"));
            btnDetails.setText("Entrar");
            btnDetails.getStyleClass().add("secondary");
        } else if (isFull) {
            membershipBadge.setVisible(false);
            membershipBadge.setManaged(false);
            roleLabel.setText("Esta comunidad ha alcanzado su capacidad máxima.");
            btnDetails.setText("Comunidad llena");
            btnDetails.getStyleClass().add("secondary");
            btnDetails.setDisable(true);
        } else if (isInviteOnly) {
            membershipBadge.setVisible(false);
            membershipBadge.setManaged(false);
            roleLabel.setText("Acceso restringido · Se requiere código de invitación");
            btnDetails.setText("Ingresar código");
            btnDetails.getStyleClass().add("secondary");
        } else {
            membershipBadge.setVisible(false);
            membershipBadge.setManaged(false);
            roleLabel.setText("Acceso libre · " + available + " espacio(s) disponible(s)");
            btnDetails.setText("Unirse");
            btnDetails.getStyleClass().add("primary");
        }
    }

    private static String formatCategory(String raw) {
        if (raw == null) return "Académica";
        return switch (raw.toUpperCase()) {
            case "ESTUDIO" -> "Estudio";
            case "PROYECTO" -> "Proyecto";
            case "CARRERA" -> "Carrera";
            case "INTERES" -> "Interés";
            default -> "Académica";
        };
    }

    @FXML
    private void onDetails() {
        if (onAction != null && item != null) {
            onAction.accept(item);
        } else if (item != null) {
            UiHelper.alert("Detalles del Espacio",
                    "Comunidad: " + item.name() + "\nIdentificador: " + item.slug() +
                            "\nCategoría: " + formatCategory(item.category()) +
                            "\nPrivacidad: " + ("INVITE_ONLY".equalsIgnoreCase(item.visibility()) ? "Requiere invitación" : "Pública") +
                            "\nOcupación: " + item.memberCount() + " / " + item.maxMembers() + " integrantes" +
                            "\n\nDescripción:\n" + item.description(),
                    javafx.scene.control.Alert.AlertType.INFORMATION);
        }
    }
}
