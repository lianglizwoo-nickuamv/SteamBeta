package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Tarjeta de Ficha / Coincidencia de Estudio (FXML) — SteamBeta Campus.
 */
public final class StudyMatchCardController {

    @FXML private ImageView avatarImage;
    @FXML private Label avatarInitials;
    @FXML private Label aliasLabel;
    @FXML private Label careerBadge;
    @FXML private Label visibilityBadge;
    @FXML private Label availBadge;
    @FXML private Label subjectsLabel;
    @FXML private Label interestsLabel;
    @FXML private Label helpLabel;
    @FXML private Label needsHelpLabel;
    @FXML private HBox actionsBox;
    @FXML private Button btnViewProfile;
    @FXML private Button btnContact;

    private Runnable onViewAction;
    private Runnable onContactAction;

    /** Configura la tarjeta para la ficha propia del estudiante autenticado. */
    public void setOwnProfile(ApiClient.StudyProfile m) {
        populateCommon(m, true);
        visibilityBadge.setVisible(true);
        visibilityBadge.setManaged(true);
        if (m.visible()) {
            visibilityBadge.setText("Ficha visible");
            visibilityBadge.getStyleClass().setAll("badge-resolved");
        } else {
            visibilityBadge.setText("Ficha oculta (privada)");
            visibilityBadge.getStyleClass().setAll("badge-role");
        }
        actionsBox.setVisible(false);
        actionsBox.setManaged(false);
    }

    /** Configura la tarjeta para un compañero disponible con acciones Ver ficha y Contactar. */
    public void setMatch(ApiClient.StudyProfile m, Runnable onView, Runnable onContact) {
        populateCommon(m, false);
        visibilityBadge.setVisible(false);
        visibilityBadge.setManaged(false);
        this.onViewAction = onView;
        this.onContactAction = onContact;
        actionsBox.setVisible(true);
        actionsBox.setManaged(true);
    }

    /** Compatibilidad con llamadas previas. */
    public void set(ApiClient.StudyProfile m) {
        setOwnProfile(m);
    }

    public void bindAvatar(ApiClient.StudyProfile m, ApiClient api, ExecutorService workers) {
        if (m == null) return;
        // Intenta cargar la fotografía del estudiante (si no tiene, AvatarLoader conserva las iniciales)
        AvatarLoader.load(avatarImage, avatarInitials, m.alias(), m.accountId(),
                "account:" + m.accountId(), 38.0, api, workers);
    }

    private void populateCommon(ApiClient.StudyProfile m, boolean showInterests) {
        AvatarLoader.showInitials(avatarImage, avatarInitials, m.alias());
        aliasLabel.setText(m.alias() != null ? m.alias() : "Estudiante");
        careerBadge.setText(m.declaredCareer() != null && !m.declaredCareer().isBlank() ? m.declaredCareer() : "Estudiante");
        availBadge.setText("Disponibilidad: " + (m.availability() != null && !m.availability().isBlank() ? m.availability() : "A convenir"));

        String subjectsText = (m.subjects() != null && !m.subjects().isEmpty())
                ? String.join(", ", m.subjects()) : "Sin materias declaradas";
        subjectsLabel.setText("Materias: " + subjectsText);

        if (showInterests && m.interests() != null && !m.interests().isEmpty()) {
            interestsLabel.setText("Temas de interés: " + String.join(", ", m.interests()));
            interestsLabel.setVisible(true);
            interestsLabel.setManaged(true);
        } else {
            interestsLabel.setVisible(false);
            interestsLabel.setManaged(false);
        }

        helpLabel.setText("Ofrece apoyo en: " + (m.canHelpWith() != null && !m.canHelpWith().isBlank() ? m.canHelpWith() : "No especificado"));

        if (m.needsHelpWith() != null && !m.needsHelpWith().isBlank()) {
            needsHelpLabel.setText("Necesita apoyo en: " + m.needsHelpWith());
            needsHelpLabel.setVisible(true);
            needsHelpLabel.setManaged(true);
        } else {
            needsHelpLabel.setVisible(false);
            needsHelpLabel.setManaged(false);
        }
    }

    @FXML
    private void onViewProfile() {
        if (onViewAction != null) onViewAction.run();
    }

    @FXML
    private void onContact() {
        if (onContactAction != null) onContactAction.run();
    }
}
