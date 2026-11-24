package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.time.Year;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Controlador del diálogo para registrar o editar una materia manual (FXML).
 */
public final class RegisterSubjectDialogController {

    @FXML private Label dialogTitleLabel;
    @FXML private TextField nameField;
    @FXML private TextField codeField;
    @FXML private Spinner<Integer> yearSpinner;
    @FXML private TextField termField;
    @FXML private TextField groupField;
    @FXML private TextField teacherField;
    @FXML private TextField creditsField;
    @FXML private TextField scheduleField;
    @FXML private TextArea notesArea;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private ApiClient api;
    private ExecutorService workers;
    private Consumer<ApiClient.ManualSubject> onSuccess;
    private ApiClient.ManualSubject editingSubject;

    @FXML
    public void initialize() {
        int currentYear = Math.max(2000, Math.min(2100, Year.now().getValue()));
        yearSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2000, 2100, currentYear));
    }

    public void init(ApiClient api, ExecutorService workers, Consumer<ApiClient.ManualSubject> onSuccess) {
        this.api = api;
        this.workers = workers;
        this.onSuccess = onSuccess;
        this.editingSubject = null;
        dialogTitleLabel.setText("Registrar Materia Manualmente");
        btnSubmit.setText("Guardar Materia");
    }

    public void initForEdit(ApiClient api, ExecutorService workers, ApiClient.ManualSubject existing,
                            Consumer<ApiClient.ManualSubject> onSuccess) {
        this.api = api;
        this.workers = workers;
        this.onSuccess = onSuccess;
        this.editingSubject = existing;
        dialogTitleLabel.setText("Editar Materia Manual");
        btnSubmit.setText("Guardar Cambios");

        if (existing != null) {
            nameField.setText(existing.name() != null ? existing.name() : "");
            codeField.setText(existing.code() != null ? existing.code() : "");
            if (yearSpinner.getValueFactory() != null) {
                yearSpinner.getValueFactory().setValue(existing.academicYear());
            }
            termField.setText(existing.termLabel() != null ? existing.termLabel() : "");
            groupField.setText(existing.groupSection() != null ? existing.groupSection() : "");
            teacherField.setText(existing.teacherName() != null ? existing.teacherName() : "");
            creditsField.setText(existing.credits() != null ? String.valueOf(existing.credits()) : "");
            scheduleField.setText(existing.schedule() != null ? existing.schedule() : "");
            notesArea.setText(existing.notes() != null ? existing.notes() : "");
        }
    }

    @FXML
    private void onSubmit() {
        hideError();
        String name = text(nameField);
        if (name.length() < 2 || name.length() > 120) {
            showError("El nombre de la materia es obligatorio y debe tener entre 2 y 120 caracteres.");
            return;
        }
        String code = optionalText(codeField);
        if (code != null && code.length() > 32) {
            showError("El código de la materia no puede superar 32 caracteres.");
            return;
        }

        int academicYear;
        try {
            yearSpinner.increment(0); // commit editor text
            Integer val = yearSpinner.getValue();
            if (val == null || val < 2000 || val > 2100) {
                showError("El año académico debe estar entre 2000 y 2100.");
                return;
            }
            academicYear = val;
        } catch (Exception ex) {
            showError("Ingresa un año académico numérico válido (2000–2100).");
            return;
        }

        String term = text(termField);
        if (term.isEmpty() || term.length() > 40) {
            showError("El período académico o semestre es obligatorio (máx. 40 caracteres).");
            return;
        }

        String group = optionalText(groupField);
        if (group != null && group.length() > 40) {
            showError("El grupo o sección no puede superar 40 caracteres.");
            return;
        }
        String teacher = optionalText(teacherField);
        if (teacher != null && teacher.length() > 120) {
            showError("El nombre del docente no puede superar 120 caracteres.");
            return;
        }

        Integer credits = null;
        String creditsRaw = optionalText(creditsField);
        if (creditsRaw != null) {
            try {
                int parsed = Integer.parseInt(creditsRaw);
                if (parsed < 0 || parsed > 40) {
                    showError("Los créditos deben ser un número entero entre 0 y 40.");
                    return;
                }
                credits = parsed;
            } catch (NumberFormatException nfe) {
                showError("Los créditos deben ser un número entero válido (o dejarse vacío).");
                return;
            }
        }

        String schedule = optionalText(scheduleField);
        if (schedule != null && schedule.length() > 160) {
            showError("El horario no puede superar 160 caracteres.");
            return;
        }
        String notes = notesArea.getText() == null ? null : notesArea.getText().strip();
        if (notes != null && notes.isEmpty()) notes = null;
        if (notes != null && notes.length() > 1500) {
            showError("La descripción o notas personales no pueden superar 1500 caracteres.");
            return;
        }

        setBusy(true);
        final Integer finalCredits = credits;
        final String finalNotes = notes;
        workers.submit(() -> {
            try {
                ApiClient.ManualSubject saved = (editingSubject == null)
                        ? api.createManualSubject(name, code, academicYear, term, group, teacher, finalCredits, schedule, finalNotes)
                        : api.updateManualSubject(editingSubject.id(), name, code, academicYear, term, group, teacher, finalCredits, schedule, finalNotes);
                Platform.runLater(() -> {
                    setBusy(false);
                    if (onSuccess != null) onSuccess.accept(saved);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setBusy(false);
                    showError(ex.getMessage() != null ? ex.getMessage() : "No se pudo guardar la materia.");
                });
            }
        });
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void setBusy(boolean busy) {
        btnSubmit.setDisable(busy);
        btnCancel.setDisable(busy);
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private static String text(TextField tf) {
        return tf.getText() == null ? "" : tf.getText().strip();
    }

    private static String optionalText(TextField tf) {
        String s = text(tf);
        return s.isEmpty() ? null : s;
    }

    private void close() {
        if (btnCancel.getScene() != null && btnCancel.getScene().getWindow() instanceof Stage s) {
            s.close();
        }
    }
}
