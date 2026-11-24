package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Diálogo de solo lectura para consultar la ficha pública de un compañero
 * y acceder al contacto por correo electrónico únicamente si está habilitado como público.
 */
public final class StudyProfileDetailDialogController {

    @FXML private Label aliasLabel;
    @FXML private Label careerBadge;
    @FXML private Label universityLabel;
    @FXML private Label loadingLabel;
    @FXML private VBox detailCard;
    @FXML private Label subjectsLabel;
    @FXML private Label interestsLabel;
    @FXML private Label canHelpLabel;
    @FXML private Label needsHelpLabel;
    @FXML private Label availabilityLabel;
    @FXML private VBox bioBox;
    @FXML private Label bioLabel;
    @FXML private VBox contactCard;
    @FXML private Label contactInfoLabel;
    @FXML private HBox contactActionsBox;
    @FXML private Button btnSendEmail;
    @FXML private Button btnCopyEmail;
    @FXML private Label copyFeedbackLabel;

    private String publicEmail;
    private String studentAlias;

    public void load(ApiClient api, ExecutorService workers, ApiClient.StudyProfile preview, boolean focusContact) {
        if (preview != null) {
            this.studentAlias = preview.alias();
            aliasLabel.setText(preview.alias() != null ? preview.alias() : "Estudiante");
            careerBadge.setText(preview.declaredCareer() != null && !preview.declaredCareer().isBlank()
                    ? preview.declaredCareer() : "Estudiante");
            universityLabel.setText(preview.declaredUniversity() != null && !preview.declaredUniversity().isBlank()
                    ? preview.declaredUniversity() : "Universidad no declarada");
        }

        loadingLabel.setVisible(true);
        loadingLabel.setManaged(true);
        loadingLabel.setText("Consultando ficha pública del compañero…");

        workers.submit(() -> {
            try {
                var detail = api.studyMatchDetail(preview.accountId());
                Platform.runLater(() -> renderDetail(detail, focusContact));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loadingLabel.setText(classifyDetailError(ex));
                    loadingLabel.getStyleClass().setAll("status-error");
                });
            }
        });
    }

    private void renderDetail(ApiClient.StudyMatchDetail d, boolean focusContact) {
        loadingLabel.setVisible(false);
        loadingLabel.setManaged(false);
        detailCard.setVisible(true);
        detailCard.setManaged(true);
        contactCard.setVisible(true);
        contactCard.setManaged(true);

        this.studentAlias = d.alias();
        aliasLabel.setText(d.alias() != null ? d.alias() : "Estudiante");
        careerBadge.setText(d.declaredCareer() != null && !d.declaredCareer().isBlank() ? d.declaredCareer() : "Estudiante");
        universityLabel.setText(d.declaredUniversity() != null && !d.declaredUniversity().isBlank()
                ? d.declaredUniversity() : "Universidad no declarada");

        subjectsLabel.setText(joinOrDefault(d.subjects(), "Sin materias registradas"));
        interestsLabel.setText(joinOrDefault(d.interests(), "Sin temas de interés registrados"));
        canHelpLabel.setText(nonBlankOrDefault(d.canHelpWith(), "No especificado"));
        needsHelpLabel.setText(nonBlankOrDefault(d.needsHelpWith(), "No especificado"));
        availabilityLabel.setText(nonBlankOrDefault(d.availability(), "A convenir"));

        if (d.bio() != null && !d.bio().isBlank()) {
            bioLabel.setText(d.bio());
            bioBox.setVisible(true);
            bioBox.setManaged(true);
        } else {
            bioBox.setVisible(false);
            bioBox.setManaged(false);
        }

        if (d.contactEmailEnabled() && d.publicEmail() != null && !d.publicEmail().isBlank()) {
            this.publicEmail = d.publicEmail().strip();
            contactInfoLabel.setText("Correo electrónico público: " + this.publicEmail);
            contactActionsBox.setVisible(true);
            contactActionsBox.setManaged(true);
            if (focusContact) {
                Platform.runLater(btnSendEmail::requestFocus);
            }
        } else {
            this.publicEmail = null;
            contactInfoLabel.setText("Este estudiante no ha habilitado el contacto por correo electrónico.");
            contactActionsBox.setVisible(false);
            contactActionsBox.setManaged(false);
        }
    }

    @FXML
    private void onSendEmail() {
        if (publicEmail == null || publicEmail.isBlank()) return;
        try {
            String subject = URLEncoder.encode("Contacto de estudio — SteamBeta Campus", StandardCharsets.UTF_8).replace("+", "%20");
            String mailto = "mailto:" + publicEmail + "?subject=" + subject;
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", mailto).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", mailto).start();
            } else {
                new ProcessBuilder("xdg-open", mailto).start();
            }
        } catch (Exception ex) {
            showFeedback("No se pudo abrir el cliente de correo. Puedes copiar la dirección.");
        }
    }

    @FXML
    private void onCopyEmail() {
        if (publicEmail == null || publicEmail.isBlank()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(publicEmail);
        Clipboard.getSystemClipboard().setContent(content);
        showFeedback("Correo copiado al portapapeles.");
    }

    @FXML
    private void onClose() {
        if (aliasLabel.getScene() != null && aliasLabel.getScene().getWindow() instanceof Stage s) {
            s.close();
        }
    }

    private void showFeedback(String text) {
        copyFeedbackLabel.setText(text);
        copyFeedbackLabel.setVisible(true);
        copyFeedbackLabel.setManaged(true);
    }

    private static String classifyDetailError(Throwable ex) {
        if (ex instanceof ApiClient.Failure f) {
            if (f.status() == 404) {
                String msg = f.getMessage();
                if (msg != null && !msg.isBlank() && !msg.equalsIgnoreCase("Not Found")) {
                    return msg;
                }
                return "La ficha de estudio no está disponible, es privada o el servidor backend necesita reiniciarse para habilitar este endpoint.";
            }
            if (f.status() == 401 || f.status() == 403) {
                return "Tu sesión no está autorizada para consultar esta ficha. Inicia sesión nuevamente.";
            }
            if (f.status() >= 500) {
                return "Error técnico en el servidor al consultar la ficha de estudio (HTTP " + f.status() + ").";
            }
            return "No se pudo consultar la ficha (HTTP " + f.status() + ").";
        }
        return "Error de conexión con el servidor al consultar la ficha del estudiante.";
    }

    private static String joinOrDefault(List<String> items, String fallback) {
        if (items == null || items.isEmpty()) return fallback;
        String joined = String.join(", ", items).strip();
        return joined.isEmpty() ? fallback : joined;
    }

    private static String nonBlankOrDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
