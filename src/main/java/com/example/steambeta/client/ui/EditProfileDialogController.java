package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Controlador para el diálogo de edición de perfil universitario (FXML).
 * Permite editar datos personales, académicos, intereses, biografía,
 * privacidad del correo y fotografía de perfil con vista previa.
 */
public final class EditProfileDialogController {

    @FXML private ImageView avatarPreviewImage;
    @FXML private Label avatarPreviewInitials;
    @FXML private Button btnSelectPhoto;
    @FXML private Button btnRemovePhoto;
    @FXML private Label photoStatusLabel;

    @FXML private TextField aliasField;
    @FXML private TextField uniField;
    @FXML private TextField careerField;
    @FXML private TextField semesterField;
    @FXML private TextField interestsField;
    @FXML private TextArea bioArea;
    @FXML private CheckBox emailPublicCheck;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSave;

    private ApiClient api;
    private ExecutorService workers;
    private Consumer<ApiClient.Profile> onSaved;
    private ApiClient.Profile originalProfile;
    private Path pendingPhotoFile;
    private boolean pendingPhotoRemoval;

    public void init(ApiClient api, ExecutorService workers, Consumer<ApiClient.Profile> onSaved) {
        this.api = api;
        this.workers = workers;
        this.onSaved = onSaved;
    }

    public void setProfile(ApiClient.Profile p) {
        if (p == null) return;
        this.originalProfile = p;
        this.pendingPhotoFile = null;
        this.pendingPhotoRemoval = false;

        aliasField.setText(p.alias() != null ? p.alias() : "");
        uniField.setText(p.declaredUniversity() != null ? p.declaredUniversity() : "");
        careerField.setText(p.declaredCareer() != null ? p.declaredCareer() : "");
        semesterField.setText(p.declaredSemester() != null ? String.valueOf(p.declaredSemester()) : "");
        interestsField.setText(p.academicInterests() != null ? p.academicInterests() : "");
        bioArea.setText(p.bio() != null ? p.bio() : "");
        emailPublicCheck.setSelected(p.emailPublic());

        boolean hasAvatar = p.avatarUrl() != null && !p.avatarUrl().isBlank();
        AvatarLoader.load(avatarPreviewImage, avatarPreviewInitials, p.alias(), p.accountId(), p.avatarUrl(), 62.0, api, workers);
        if (btnRemovePhoto != null) {
            btnRemovePhoto.setVisible(hasAvatar);
            btnRemovePhoto.setManaged(hasAvatar);
        }
        if (photoStatusLabel != null) {
            photoStatusLabel.setText(hasAvatar
                    ? "Fotografía actual cargada · JPG, PNG o WEBP (máx. 5 MB)."
                    : "Sin fotografía · JPG, PNG o WEBP (máx. 5 MB).");
        }
    }

    @FXML
    private void onSelectPhoto() {
        Stage owner = btnSave.getScene() != null ? (Stage) btnSave.getScene().getWindow() : null;
        File chosen = AvatarLoader.createAvatarFileChooser().showOpenDialog(owner);
        if (chosen == null) return;
        if (chosen.length() > 5L * 1024L * 1024L) {
            showError("La fotografía supera el límite máximo permitido de 5 MB.");
            return;
        }
        this.pendingPhotoFile = chosen.toPath();
        this.pendingPhotoRemoval = false;
        AvatarLoader.showLocalPreview(avatarPreviewImage, avatarPreviewInitials, pendingPhotoFile, 62.0, getAlias());
        if (btnRemovePhoto != null) {
            btnRemovePhoto.setVisible(true);
            btnRemovePhoto.setManaged(true);
        }
        if (photoStatusLabel != null) {
            photoStatusLabel.setText("Nueva fotografía seleccionada: " + chosen.getName());
        }
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    @FXML
    private void onRemovePhoto() {
        this.pendingPhotoFile = null;
        this.pendingPhotoRemoval = true;
        AvatarLoader.showInitials(avatarPreviewImage, avatarPreviewInitials, getAlias());
        if (btnRemovePhoto != null) {
            btnRemovePhoto.setVisible(false);
            btnRemovePhoto.setManaged(false);
        }
        if (photoStatusLabel != null) {
            photoStatusLabel.setText("Fotografía marcada para eliminarse al guardar.");
        }
    }

    public String getAlias() {
        return aliasField.getText() == null ? "" : aliasField.getText().strip();
    }

    public String getUniversity() {
        return uniField.getText() == null ? "" : uniField.getText().strip();
    }

    public String getCareer() {
        return careerField.getText() == null ? "" : careerField.getText().strip();
    }

    public Integer getSemester() {
        try {
            String s = semesterField.getText() == null ? "" : semesterField.getText().strip();
            return s.isBlank() ? null : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getInterests() {
        return interestsField.getText() == null ? "" : interestsField.getText().strip();
    }

    public String getBio() {
        return bioArea.getText() == null ? "" : bioArea.getText().strip();
    }

    public boolean isEmailPublic() {
        return emailPublicCheck.isSelected();
    }

    @FXML
    private void onSave() {
        String al = getAlias();
        if (al.length() < 2 || al.length() > 60) {
            showError("El alias debe tener entre 2 y 60 caracteres.");
            return;
        }

        String semText = semesterField.getText() == null ? "" : semesterField.getText().strip();
        Integer sem = null;
        if (!semText.isBlank()) {
            try {
                sem = Integer.parseInt(semText);
                if (sem < 1 || sem > 20) {
                    showError("El semestre debe ser un número entero entre 1 y 20.");
                    return;
                }
            } catch (NumberFormatException ex) {
                showError("El semestre debe ser un número entero entre 1 y 20.");
                return;
            }
        }

        String bio = getBio();
        if (bio.length() > 500) {
            showError("La biografía no puede superar los 500 caracteres.");
            return;
        }

        String uni = getUniversity();
        String car = getCareer();
        String interests = getInterests();
        boolean pub = isEmailPublic();
        final Integer finalSem = sem;
        final Path photoToUpload = pendingPhotoFile;
        final boolean removePhoto = pendingPhotoRemoval;
        final String prevUrl = originalProfile != null ? originalProfile.avatarUrl() : null;

        if (api == null || workers == null) return;

        btnSave.setDisable(true);
        btnCancel.setDisable(true);
        if (btnSelectPhoto != null) btnSelectPhoto.setDisable(true);
        if (btnRemovePhoto != null) btnRemovePhoto.setDisable(true);
        showStatus("Guardando perfil en PostgreSQL...", "status-pending");

        workers.submit(() -> {
            try {
                var updated = api.updateProfile(al, uni, car, finalSem, bio, null, interests, pub);
                if (photoToUpload != null) {
                    updated = api.uploadAvatar(photoToUpload);
                    AvatarLoader.evictCache(prevUrl);
                } else if (removePhoto && prevUrl != null && !prevUrl.isBlank()) {
                    updated = api.deleteAvatar();
                    AvatarLoader.evictCache(prevUrl);
                }
                final var finalUpdated = updated;
                Platform.runLater(() -> {
                    if (onSaved != null) onSaved.accept(finalUpdated);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSave.setDisable(false);
                    btnCancel.setDisable(false);
                    if (btnSelectPhoto != null) btnSelectPhoto.setDisable(false);
                    if (btnRemovePhoto != null) btnRemovePhoto.setDisable(false);
                    showError(ex.getMessage() != null ? ex.getMessage() : "Error al actualizar perfil.");
                });
            }
        });
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void showError(String message) {
        showStatus(message, "status-error");
    }

    private void showStatus(String message, String styleClass) {
        errorLabel.setText(message);
        errorLabel.getStyleClass().setAll(styleClass);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void close() {
        if (btnSave.getScene() != null && btnSave.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }
}
