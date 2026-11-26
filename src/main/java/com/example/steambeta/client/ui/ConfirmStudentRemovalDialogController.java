package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Diálogo modal de vista previa y confirmación explícita ("ELIMINAR") para
 * el borrado físico real de la cuenta de un estudiante en STEAMBETA ADMIN,
 * conservando sus aportes compartidos bajo el autor "Usuario eliminado".
 */
public final class ConfirmStudentRemovalDialogController {

    @FXML private Label lblIdentity;
    @FXML private Label lblEmailAndDate;
    @FXML private Label lblPersonalEliminated;
    @FXML private Label lblSharedAnonymized;
    @FXML private Label lblInstitutionalRetained;
    @FXML private TextField reasonField;
    @FXML private Label lblConfirmPrompt;
    @FXML private TextField confirmField;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnConfirmRemove;

    private boolean confirmed = false;

    @FXML
    public void initialize() {
        confirmField.textProperty().addListener((obs, oldV, newV) -> {
            boolean matches = newV != null && "ELIMINAR".equals(newV.strip());
            btnConfirmRemove.setDisable(!matches);
            if (matches) UiHelper.setVisible(errorLabel, false);
        });
    }

    public void setPreview(ApiClient.AdminStudentRemovalPreview p) {
        if (p == null) return;
        lblIdentity.setText("Alias: " + safe(p.alias()) + "  ·  Login: " + safe(p.loginIdentifier())
                + "\nIdentificador interno: " + p.accountId());
        String emailText = (p.email() != null && !p.email().isBlank()) ? p.email() : "No registrado";
        lblEmailAndDate.setText("Correo: " + emailText + "  ·  Fecha de registro: " + UiHelper.formatDate(p.createdAt()));

        lblPersonalEliminated.setText(
                "• Fila original de la cuenta en PostgreSQL, credenciales, correo y perfil personal.\n"
                + "• Tareas académicas privadas: " + p.tasksCount() + "\n"
                + "• Materias manuales registradas: " + p.manualSubjectsCount() + "\n"
                + "• Ficha en Equipos de Estudio: " + (p.studyProfileExists() ? "Sí (se eliminará)" : "No registrada") + "\n"
                + "• Publicaciones de intercambio personales: " + p.exchangeItemsCount()
                + "  ·  Adjuntos de intercambio: " + p.exchangeAttachmentsCount() + " (también en disco)\n"
                + "• Foto de perfil personal: " + (p.hasAvatar() ? "Sí (se eliminará en disco)" : "No registrada") + "\n"
                + "• Notificaciones y tokens de verificación: " + (p.notificationsCount() + p.emailTokensCount())
        );

        lblSharedAnonymized.setText(
                "• Membresías comunitarias desvinculadas del estudiante: " + p.communitiesCount() + "\n"
                + "• Publicaciones en comunidades: " + p.postsCount()
                + "  ·  Preguntas académicas: " + p.questionsCount()
                + "  ·  Comentarios: " + p.commentsCount() + "\n"
                + "• Recursos comunitarios compartidos (archivos intactos y descargables): " + p.resourcesCount() + "\n"
                + "• Oportunidades publicadas: " + p.opportunitiesCount()
                + "  ·  Eventos comunitarios: " + p.eventsCount()
        );

        lblInstitutionalRetained.setText(
                "• La fila original desaparecerá por completo de la base de datos y los aportes compartidos se reasignarán al autor genérico «Usuario eliminado».\n"
                + "• Sanciones asociadas: " + p.sanctionsCount()
                + "  ·  Registros de auditoría relacionados: " + p.auditLogsCount() + " (conservados para trazabilidad institucional sin datos personales)."
        );

        lblConfirmPrompt.setText("ESCRIBE ELIMINAR PARA CONFIRMAR LA ELIMINACIÓN DEFINITIVA DE «" + safe(p.alias()) + "» (" + safe(p.loginIdentifier()) + "):");
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getConfirmationText() {
        return confirmField.getText() != null ? confirmField.getText().strip() : "";
    }

    public String getReason() {
        return reasonField.getText() != null ? reasonField.getText().strip() : "";
    }

    @FXML
    private void onConfirm() {
        if (!"ELIMINAR".equals(getConfirmationText())) {
            errorLabel.setText("Debes escribir exactamente ELIMINAR para continuar.");
            UiHelper.setVisible(errorLabel, true);
            return;
        }
        String r = getReason();
        if (!r.isBlank() && (r.length() < 5 || r.length() > 2000)) {
            errorLabel.setText("Si indicas un motivo, debe tener entre 5 y 2000 caracteres.");
            UiHelper.setVisible(errorLabel, true);
            return;
        }
        confirmed = true;
        close();
    }

    @FXML
    private void onCancel() {
        confirmed = false;
        close();
    }

    private void close() {
        if (confirmField.getScene() != null && confirmField.getScene().getWindow() instanceof Stage s) {
            s.close();
        }
    }

    private static String safe(String s) {
        return (s != null && !s.isBlank()) ? s : "—";
    }
}
