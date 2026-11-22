package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Controlador para el diálogo de edición de perfil universitario (FXML).
 */
public final class EditProfileDialogController {

    @FXML private TextField aliasField;
    @FXML private TextField uniField;
    @FXML private TextField careerField;
    @FXML private TextField semesterField;
    @FXML private TextArea bioArea;
    @FXML private CheckBox emailPublicCheck;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSave;

    private ApiClient api;
    private ExecutorService workers;
    private Consumer<ApiClient.Profile> onSaved;

    public void init(ApiClient api, ExecutorService workers, Consumer<ApiClient.Profile> onSaved) {
        this.api = api;
        this.workers = workers;
        this.onSaved = onSaved;
    }

    public void setProfile(ApiClient.Profile p) {
        if (p == null) return;
        aliasField.setText(p.alias() != null ? p.alias() : "");
        uniField.setText(p.declaredUniversity() != null ? p.declaredUniversity() : "");
        careerField.setText(p.declaredCareer() != null ? p.declaredCareer() : "");
        semesterField.setText(p.declaredSemester() != null ? String.valueOf(p.declaredSemester()) : "");
        bioArea.setText(p.bio() != null ? p.bio() : "");
        emailPublicCheck.setSelected(p.emailPublic());
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

        Integer sem = getSemester();
        if (sem != null && (sem < 1 || sem > 20)) {
            showError("El semestre debe ser un número entero entre 1 y 20.");
            return;
        }

        String bio = getBio();
        if (bio.length() > 500) {
            showError("La biografía no puede superar los 500 caracteres.");
            return;
        }

        String uni = getUniversity();
        String car = getCareer();
        boolean pub = isEmailPublic();

        if (api == null || workers == null) return;

        btnSave.setDisable(true);
        btnCancel.setDisable(true);
        showError("Guardando perfil en PostgreSQL...", "#38bdf8");

        workers.submit(() -> {
            try {
                var updated = api.updateProfile(al, uni, car, sem, bio, null, null, pub);
                Platform.runLater(() -> {
                    if (onSaved != null) onSaved.accept(updated);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSave.setDisable(false);
                    btnCancel.setDisable(false);
                    showError(ex.getMessage(), "#f87171");
                });
            }
        });
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void showError(String message) {
        showError(message, "#f87171");
    }

    private void showError(String message, String color) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        errorLabel.setVisible(true);
    }

    private void close() {
        if (btnSave.getScene() != null && btnSave.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }
}
