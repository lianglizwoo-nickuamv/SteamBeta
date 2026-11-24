package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Controlador del diálogo de creación de tareas académicas — SteamBeta Campus.
 * Sustituye el TextInputDialog nativo. 100% FXML + CSS.
 * Prioridades en español mapeadas internamente a enums estándar.
 * Fechas calculadas en la zona horaria institucional America/Managua.
 */
public final class CreateTaskDialogController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private ComboBox<String> subjectCombo;
    @FXML private DatePicker dueDatePicker;
    @FXML private TextField projectField;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private ApiClient api;
    private ExecutorService workers;
    private Consumer<ApiClient.TaskItem> onSuccess;
    private ApiClient.TaskItem editingTask;

    private static final Map<String, String> PRIORITY_LABELS_TO_ENUM = Map.of(
            "Baja", "LOW",
            "Media", "MEDIUM",
            "Alta", "HIGH",
            "Urgente", "URGENT"
    );

    @FXML
    public void initialize() {
        priorityCombo.setItems(FXCollections.observableArrayList("Baja", "Media", "Alta", "Urgente"));
        priorityCombo.setValue("Media");
        if (subjectCombo != null) {
            subjectCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank()) {
                    projectField.setText(newVal);
                }
            });
        }
    }

    public void init(ApiClient api, ExecutorService workers, Consumer<ApiClient.TaskItem> onSuccess) {
        this.api = api;
        this.workers = workers;
        this.onSuccess = onSuccess;
        this.editingTask = null;
        loadRegisteredSubjects();
    }

    public void initForEdit(ApiClient api, ExecutorService workers, ApiClient.TaskItem existing,
                            Consumer<ApiClient.TaskItem> onSuccess) {
        this.api = api;
        this.workers = workers;
        this.onSuccess = onSuccess;
        this.editingTask = existing;
        loadRegisteredSubjects();
        if (existing != null) {
            titleField.setText(existing.title() != null ? existing.title() : "");
            descriptionArea.setText(existing.description() != null ? existing.description() : "");
            projectField.setText(existing.projectName() != null ? existing.projectName() : "");
            String label = switch (existing.priority() != null ? existing.priority().toUpperCase() : "MEDIUM") {
                case "LOW" -> "Baja";
                case "HIGH" -> "Alta";
                case "URGENT" -> "Urgente";
                default -> "Media";
            };
            priorityCombo.setValue(label);
            if (existing.dueDate() != null && !existing.dueDate().isBlank()) {
                try {
                    LocalDate ld = java.time.Instant.parse(existing.dueDate())
                            .atZone(ZoneId.of("America/Managua")).toLocalDate();
                    dueDatePicker.setValue(ld);
                } catch (Exception ignored) {}
            }
            btnSubmit.setText("Guardar Cambios");
        }
    }

    @FXML
    private void onSubmit() {
        String title = titleField.getText() == null ? "" : titleField.getText().strip();
        if (title.length() < 2 || title.length() > 160) {
            showError("El título debe tener entre 2 y 160 caracteres.");
            return;
        }

        String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().strip();
        if (description.length() > 3000) {
            showError("La descripción no puede superar 3000 caracteres.");
            return;
        }

        String selectedPriorityLabel = priorityCombo.getValue() != null ? priorityCombo.getValue() : "Media";
        String priorityEnum = PRIORITY_LABELS_TO_ENUM.getOrDefault(selectedPriorityLabel, "MEDIUM");

        String dueDateIso = null;
        if (dueDatePicker.getValue() != null) {
            LocalDate date = dueDatePicker.getValue();
            try {
                ZonedDateTime zdt = date.atTime(23, 59, 59).atZone(ZoneId.of("America/Managua"));
                dueDateIso = zdt.toInstant().toString();
            } catch (Exception ex) {
                showError("Fecha no válida.");
                return;
            }
        }

        String project = projectField.getText() == null ? "General" : projectField.getText().strip();
        if (project.isBlank()) project = "General";
        if (project.length() > 100) {
            showError("El nombre del proyecto no puede superar 100 caracteres.");
            return;
        }

        btnSubmit.setDisable(true);
        btnCancel.setDisable(true);
        showInfo("Registrando tarea en PostgreSQL…");

        final String finalTitle = title;
        final String finalDesc = description;
        final String finalPriority = priorityEnum;
        final String finalDue = dueDateIso;
        final String finalProject = project;

        workers.submit(() -> {
            try {
                final ApiClient.TaskItem saved;
                if (editingTask != null) {
                    saved = api.updateTaskFull(editingTask.id(), finalTitle, finalDesc, null,
                            finalPriority, finalDue, finalProject, finalDue == null);
                } else {
                    saved = api.createTask(finalTitle, finalDesc, finalPriority, finalDue, finalProject);
                }
                Platform.runLater(() -> {
                    if (onSuccess != null) onSuccess.accept(saved);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSubmit.setDisable(false);
                    btnCancel.setDisable(false);
                    String cause = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage()
                            : "Error de conexión o fallo interno del servidor al crear la tarea.";
                    showError("No se pudo registrar la tarea: " + cause);
                });
            }
        });
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.getStyleClass().setAll("status-error");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void showInfo(String msg) {
        errorLabel.setText(msg);
        errorLabel.getStyleClass().setAll("muted");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void loadRegisteredSubjects() {
        if (subjectCombo == null || api == null || workers == null) return;
        workers.submit(() -> {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            try {
                for (var s : api.library()) {
                    if (s.name() != null && !s.name().isBlank()) names.add(s.name().strip());
                }
            } catch (Exception ignored) {}
            try {
                for (var m : api.manualSubjects(null, null)) {
                    if (m.name() != null && !m.name().isBlank()) names.add(m.name().strip());
                }
            } catch (Exception ignored) {}
            Platform.runLater(() -> {
                if (names.isEmpty()) {
                    subjectCombo.setVisible(false);
                    subjectCombo.setManaged(false);
                } else {
                    subjectCombo.setItems(FXCollections.observableArrayList(names));
                    subjectCombo.setVisible(true);
                    subjectCombo.setManaged(true);
                }
            });
        });
    }

    private void close() {
        if (titleField.getScene() != null && titleField.getScene().getWindow() instanceof Stage s) {
            s.close();
        }
    }
}
