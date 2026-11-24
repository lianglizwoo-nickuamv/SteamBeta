package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import java.util.function.Consumer;

/**
 * Controlador de Tarjeta de Tarea Kanban (FXML) — SteamBeta Campus.
 */
public final class TaskCardController {

    @FXML private Label priorityBadge;
    @FXML private Label dueLabel;
    @FXML private Label titleLabel;
    @FXML private Label projectBadge;
    @FXML private Label descLabel;

    @FXML private Button btnProg;
    @FXML private Button btnRev;
    @FXML private Button btnComp;
    @FXML private Button btnEdit;
    @FXML private Button btnDel;

    private Consumer<String> onStatusChange;
    private Runnable onEditAction;
    private Runnable onDelete;

    public void set(ApiClient.TaskItem task, Consumer<String> onStatusChange, Runnable onDelete) {
        set(task, onStatusChange, null, onDelete);
    }

    public void set(ApiClient.TaskItem task, Consumer<String> onStatusChange, Runnable onEdit, Runnable onDelete) {
        this.onStatusChange = onStatusChange;
        this.onEditAction = onEdit;
        this.onDelete = onDelete;

        String priorityText = switch (task.priority() != null ? task.priority().toUpperCase() : "MEDIUM") {
            case "LOW" -> "Baja";
            case "HIGH" -> "Alta";
            case "URGENT" -> "Urgente";
            default -> "Media";
        };
        priorityBadge.setText(priorityText);
        dueLabel.setText(task.dueDate() != null ? "Vence: " + UiHelper.formatDateShort(task.dueDate()) : "Sin fecha");
        titleLabel.setText(task.title());

        if (task.projectName() != null && !task.projectName().isBlank()) {
            projectBadge.setText("Asignatura: " + task.projectName());
            projectBadge.setVisible(true);
            projectBadge.setManaged(true);
        } else {
            projectBadge.setVisible(false);
            projectBadge.setManaged(false);
        }

        if (task.description() != null && !task.description().isBlank()) {
            descLabel.setText(task.description());
            descLabel.setVisible(true);
            descLabel.setManaged(true);
        } else {
            descLabel.setVisible(false);
            descLabel.setManaged(false);
        }

        boolean showProg = !"IN_PROGRESS".equals(task.status());
        boolean showRev = !"IN_REVIEW".equals(task.status()) && !"COMPLETED".equals(task.status());
        boolean showComp = !"COMPLETED".equals(task.status());

        btnProg.setVisible(showProg);
        btnProg.setManaged(showProg);
        if (btnRev != null) {
            btnRev.setVisible(showRev);
            btnRev.setManaged(showRev);
        }
        btnComp.setVisible(showComp);
        btnComp.setManaged(showComp);
        if (btnEdit != null) {
            btnEdit.setVisible(onEdit != null);
            btnEdit.setManaged(onEdit != null);
        }
    }

    @FXML private void onProgress() { if (onStatusChange != null) onStatusChange.accept("IN_PROGRESS"); }
    @FXML private void onReview() { if (onStatusChange != null) onStatusChange.accept("IN_REVIEW"); }
    @FXML private void onComplete() { if (onStatusChange != null) onStatusChange.accept("COMPLETED"); }
    @FXML private void onEdit() { if (onEditAction != null) onEditAction.run(); }
    @FXML private void onDelete() { if (onDelete != null) onDelete.run(); }
}
