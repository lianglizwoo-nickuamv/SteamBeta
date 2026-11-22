package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Perfil Estudiantil (FXML) — SteamBeta Campus:
 * Visualización y edición de datos académicos y correo privado en PostgreSQL.
 * Construcción 100% basada en FXML.
 */
public final class ProfileController {

    @FXML private VBox profileContainer;
    @FXML private Label statusLabel;
    @FXML private StackPane avatarCircle;
    @FXML private Label avatarInitials;
    @FXML private Label aliasLabel;
    @FXML private Label handleLabel;
    @FXML private Label roleBadge;
    @FXML private Label emailLabel;
    @FXML private Label emailTag;
    @FXML private Label universityLabel;
    @FXML private Label careerLabel;
    @FXML private Label semesterLabel;
    @FXML private Label bioLabel;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private MainLayoutController navigator;
    private ApiClient.Profile currentProfile;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers, MainLayoutController navigator) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.navigator = navigator;

        loadProfile();
    }

    private void loadProfile() {
        long epoch = scope.generation();
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        statusLabel.setText("Cargando tu perfil estudiantil desde PostgreSQL…");

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
        String initials = (p.alias() != null && !p.alias().isBlank())
                ? p.alias().substring(0, Math.min(2, p.alias().length())).toUpperCase() : "ST";
        avatarInitials.setText(initials);

        aliasLabel.setText(p.alias() != null ? p.alias() : "Estudiante");
        handleLabel.setText(p.login() != null ? "@" + p.login() : "@estudiante");
        roleBadge.setText(p.globalRole() != null ? p.globalRole() : "USER");

        String emailDisplay = (p.email() != null && !p.email().isBlank()) ? p.email() : "Sin correo registrado";
        emailLabel.setText(emailDisplay);
        emailTag.getStyleClass().removeAll("tag-pill", "brand-badge");
        if (p.emailPublic()) {
            emailTag.setText("🌐 Visible en el campus");
            emailTag.getStyleClass().add("tag-pill");
        } else {
            emailTag.setText("🔒 Correo privado (Solo tú lo ves)");
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
    private void onRefresh() {
        loadProfile();
    }

    @FXML
    private void onEditProfile() {
        if (currentProfile == null) return;

        var dialogLoader = ViewLoader.<EditProfileDialogController>load("EditProfileDialog.fxml");
        dialogLoader.controller().setProfile(currentProfile);

        Stage owner = profileContainer.getScene() != null ? (Stage) profileContainer.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(dialogLoader.root(), "Editar Perfil Universitario", owner);

        dialogLoader.controller().init(api, workers, updated -> {
            loadProfile();
            UiHelper.showSuccess(owner, "Perfil Actualizado", "Tus datos académicos han sido actualizados correctamente en PostgreSQL.");
        });

        modal.showAndWait();
    }
}
