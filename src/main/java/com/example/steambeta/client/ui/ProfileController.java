package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Perfil Estudiantil (FXML) — SteamBeta Campus:
 * Visualización y edición de identidad estudiantil, fotografía de perfil,
 * trayectoria académica, intereses, biografía y privacidad de correo.
 */
public final class ProfileController {

    @FXML private VBox profileContainer;
    @FXML private Label statusLabel;
    @FXML private StackPane avatarCircle;
    @FXML private ImageView avatarImage;
    @FXML private Label avatarInitials;
    @FXML private Label aliasLabel;
    @FXML private Label handleLabel;
    @FXML private Label roleBadge;
    @FXML private Label verifiedBadge;
    @FXML private Label academicSummaryLabel;
    @FXML private Label communitiesCountBadge;
    @FXML private Label postsCountBadge;
    @FXML private Button btnChangePhoto;
    @FXML private Button btnDeletePhoto;

    @FXML private Label interestsLabel;
    @FXML private Label emailLabel;
    @FXML private Label emailTag;
    @FXML private Label universityLabel;
    @FXML private Label careerLabel;
    @FXML private Label semesterLabel;
    @FXML private Label bioLabel;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private ApiClient.Profile currentProfile;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers, MainLayoutController navigator) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        loadProfile();
    }

    private void loadProfile() {
        long epoch = scope.generation();
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        statusLabel.setText("Cargando tu perfil estudiantil desde PostgreSQL...");

        workers.submit(() -> {
            try {
                var p = api.profile();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    this.currentProfile = p;
                    renderProfile(p);
                    statusLabel.setVisible(false);
                    statusLabel.setManaged(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    statusLabel.setVisible(true);
                    statusLabel.setManaged(true);
                    statusLabel.setText("No se pudo cargar el perfil universitario.");
                });
            }
        });
    }

    private void renderProfile(ApiClient.Profile p) {
        boolean hasAvatar = p.avatarUrl() != null && !p.avatarUrl().isBlank();
        AvatarLoader.load(avatarImage, avatarInitials, p.alias(), p.accountId(), p.avatarUrl(), 78.0, api, workers);
        if (btnDeletePhoto != null) {
            btnDeletePhoto.setVisible(hasAvatar);
            btnDeletePhoto.setManaged(hasAvatar);
        }

        aliasLabel.setText(p.alias() != null ? p.alias() : "Estudiante");
        handleLabel.setText(p.login() != null ? "@" + p.login() : "@estudiante");
        roleBadge.setText(p.globalRole() != null ? p.globalRole() : "USER");

        if (p.institutionalIdentityVerified()) {
            verifiedBadge.setVisible(true);
            verifiedBadge.setManaged(true);
        } else {
            verifiedBadge.setVisible(false);
            verifiedBadge.setManaged(false);
        }

        if (academicSummaryLabel != null) {
            List<String> summaryParts = new ArrayList<>();
            if (p.declaredCareer() != null && !p.declaredCareer().isBlank()) summaryParts.add(p.declaredCareer());
            if (p.declaredUniversity() != null && !p.declaredUniversity().isBlank()) summaryParts.add(p.declaredUniversity());
            if (p.declaredSemester() != null) summaryParts.add("Semestre " + p.declaredSemester());
            academicSummaryLabel.setText(summaryParts.isEmpty()
                    ? "Completa tu universidad, carrera y semestre para construir tu ficha académica."
                    : String.join(" · ", summaryParts));
        }

        communitiesCountBadge.setText(p.communitiesCount() + " Comunidades");
        postsCountBadge.setText(p.postsCount() + " Aportes");

        if (p.academicInterests() != null && !p.academicInterests().isBlank()) {
            interestsLabel.setText(p.academicInterests());
        } else {
            interestsLabel.setText("Aún no has registrado intereses académicos. Edita tu perfil para agregarlos.");
        }

        String emailDisplay = (p.email() != null && !p.email().isBlank()) ? p.email() : "Sin correo registrado";
        emailLabel.setText(emailDisplay);
        emailTag.getStyleClass().removeAll("tag-pill", "brand-badge");
        if (p.emailPublic()) {
            emailTag.setText("Visible en el campus");
            emailTag.getStyleClass().add("tag-pill");
        } else {
            emailTag.setText("Correo confidencial");
            emailTag.getStyleClass().add("brand-badge");
        }

        universityLabel.setText(p.declaredUniversity() != null && !p.declaredUniversity().isBlank()
                ? p.declaredUniversity() : "Universidad no especificada");
        careerLabel.setText(p.declaredCareer() != null && !p.declaredCareer().isBlank()
                ? p.declaredCareer() : "Carrera no especificada");
        semesterLabel.setText(p.declaredSemester() != null
                ? "Semestre " + p.declaredSemester() : "Semestre no especificado");

        bioLabel.setText(p.bio() != null && !p.bio().isBlank()
                ? p.bio() : "Aún no has escrito una biografía. Cuéntale a tus compañeros qué estás estudiando y tus proyectos en curso.");
    }

    @FXML
    private void onChangePhoto() {
        if (currentProfile == null || api == null || workers == null) return;
        Stage owner = profileContainer.getScene() != null ? (Stage) profileContainer.getScene().getWindow() : null;
        File chosen = AvatarLoader.createAvatarFileChooser().showOpenDialog(owner);
        if (chosen == null) return;

        if (chosen.length() > 5L * 1024L * 1024L) {
            UiHelper.showError(owner, "Fotografía demasiado grande",
                    "La fotografía seleccionada supera el límite máximo permitido de 5 MB.");
            return;
        }

        // Vista previa inmediata mientras se sube al servidor
        String previousUrl = currentProfile.avatarUrl();
        AvatarLoader.showLocalPreview(avatarImage, avatarInitials, chosen.toPath(), 78.0, currentProfile.alias());
        if (btnChangePhoto != null) btnChangePhoto.setDisable(true);
        if (btnDeletePhoto != null) btnDeletePhoto.setDisable(true);
        statusLabel.setText("Subiendo fotografía de perfil…");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);

        workers.submit(() -> {
            try {
                var updated = api.uploadAvatar(chosen.toPath());
                AvatarLoader.evictCache(previousUrl);
                Platform.runLater(() -> {
                    if (btnChangePhoto != null) btnChangePhoto.setDisable(false);
                    if (btnDeletePhoto != null) btnDeletePhoto.setDisable(false);
                    this.currentProfile = updated;
                    renderProfile(updated);
                    statusLabel.setVisible(false);
                    statusLabel.setManaged(false);
                    UiHelper.showSuccess(owner, "Fotografía actualizada",
                            "Tu nueva fotografía de perfil se ha guardado correctamente en el servidor.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (btnChangePhoto != null) btnChangePhoto.setDisable(false);
                    if (btnDeletePhoto != null) btnDeletePhoto.setDisable(false);
                    renderProfile(currentProfile);
                    statusLabel.setVisible(false);
                    statusLabel.setManaged(false);
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "No se pudo subir la fotografía de perfil.";
                    UiHelper.showError(owner, "No se pudo subir la fotografía", msg);
                });
            }
        });
    }

    @FXML
    private void onDeletePhoto() {
        if (currentProfile == null || api == null || workers == null) return;
        Stage owner = profileContainer.getScene() != null ? (Stage) profileContainer.getScene().getWindow() : null;
        boolean confirmed = UiHelper.showConfirm(owner, "Eliminar fotografía de perfil",
                "¿Deseas eliminar tu fotografía de perfil? Se mostrarán nuevamente tus iniciales.");
        if (!confirmed) return;

        String previousUrl = currentProfile.avatarUrl();
        if (btnChangePhoto != null) btnChangePhoto.setDisable(true);
        if (btnDeletePhoto != null) btnDeletePhoto.setDisable(true);

        workers.submit(() -> {
            try {
                var updated = api.deleteAvatar();
                AvatarLoader.evictCache(previousUrl);
                Platform.runLater(() -> {
                    if (btnChangePhoto != null) btnChangePhoto.setDisable(false);
                    if (btnDeletePhoto != null) btnDeletePhoto.setDisable(false);
                    this.currentProfile = updated;
                    renderProfile(updated);
                    UiHelper.showSuccess(owner, "Fotografía eliminada",
                            "Tu fotografía de perfil fue eliminada. Ahora se muestran tus iniciales.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (btnChangePhoto != null) btnChangePhoto.setDisable(false);
                    if (btnDeletePhoto != null) btnDeletePhoto.setDisable(false);
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "No se pudo eliminar la fotografía.";
                    UiHelper.showError(owner, "Error al eliminar fotografía", msg);
                });
            }
        });
    }

    @FXML
    private void onRefresh() {
        loadProfile();
    }

    @FXML
    private void onEditProfile() {
        if (currentProfile == null) return;

        var dialogLoader = ViewLoader.<EditProfileDialogController>load("EditProfileDialog.fxml");
        dialogLoader.controller().init(api, workers, updated -> {
            loadProfile();
            Stage own = profileContainer.getScene() != null ? (Stage) profileContainer.getScene().getWindow() : null;
            UiHelper.showSuccess(own, "Perfil Actualizado", "Tus datos académicos han sido actualizados correctamente en PostgreSQL.");
        });
        dialogLoader.controller().setProfile(currentProfile);

        Stage owner = profileContainer.getScene() != null ? (Stage) profileContainer.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(dialogLoader.root(), "Editar Perfil Universitario", owner);
        modal.showAndWait();
    }
}
