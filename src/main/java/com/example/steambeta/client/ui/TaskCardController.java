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
    @FXML private Button btnComp;
    @FXML private Button btnDel;

    private ApiClient.TaskItem task;
    private Consumer<String> onStatusChange;
    private Runnable onDelete;

    public void set(ApiClient.TaskItem task, Consumer<String> onStatusChange, Runnable onDelete) {
        this.task = task;
        this.onStatusChange = onStatusChange;
        this.onDelete = onDelete;

        priorityBadge.setText(task.priority() != null ? task.priority() : "MEDIUM");
        dueLabel.setText(task.dueDate() != null ? "Vence: " + UiHelper.formatDateShort(task.dueDate()) : "");
        titleLabel.setText(task.title());

        if (task.projectName() != null && !task.projectName().isBlank()) {
            projectBadge.setText("Proyecto: " + task.projectName());
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

        btnProg.setVisible(!"IN_PROGRESS".equals(task.status()));
        btnProg.setManaged(!"IN_PROGRESS".equals(task.status()));
        btnComp.setVisible(!"COMPLETED".equals(task.status()));
        btnComp.setManaged(!"COMPLETED".equals(task.status()));
    }

    @FXML private void onProgress() { if (onStatusChange != null) onStatusChange.accept("IN_PROGRESS"); }
    @FXML private void onComplete() { if (onStatusChange != null) onStatusChange.accept("COMPLETED"); }
    @FXML private void onDelete() { if (onDelete != null) onDelete.run(); }
}
