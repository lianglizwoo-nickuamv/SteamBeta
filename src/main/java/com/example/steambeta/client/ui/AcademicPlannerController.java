package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.concurrent.ExecutorService;

/**
 * Controlador del Organizador Académico (FXML) — SteamBeta Campus:
 * Tablero Kanban para gestión de entregas, tareas y proyectos.
 * Sin TextInputDialog ni Alert nativos — usa CreateTaskDialog.fxml.
 */
public final class AcademicPlannerController {

    @FXML private VBox pendingCol;
    @FXML private VBox progressCol;
    @FXML private VBox reviewCol;
    @FXML private VBox completedCol;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        loadTasks();
    }

    private void loadTasks() {
        long epoch = scope.generation();
        workers.submit(() -> {
            try {
                var tasks = api.myTasks(null);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    pendingCol.getChildren().clear();
                    progressCol.getChildren().clear();
                    reviewCol.getChildren().clear();
                    completedCol.getChildren().clear();

                    if (tasks.isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("📋", "Sin tareas registradas",
                                "Pulsa '+ Nueva Tarea' para registrar una entrega o proyecto académico.",
                                null, null);
                        pendingCol.getChildren().add(empty.root());
                        return;
                    }

                    for (var t : tasks) {
                        var card = ViewLoader.<TaskCardController>load("TaskCard.fxml");
                        card.controller().set(t, status -> updateStatus(t.id(), status), () -> deleteTask(t.id()));
                        switch (t.status()) {
                            case "IN_PROGRESS" -> progressCol.getChildren().add(card.root());
                            case "IN_REVIEW"   -> reviewCol.getChildren().add(card.root());
                            case "COMPLETED"   -> completedCol.getChildren().add(card.root());
                            default            -> pendingCol.getChildren().add(card.root());
                        }
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    private void updateStatus(String taskId, String status) {
        workers.submit(() -> {
            try {
                api.updateTask(taskId, status, null, null);
                Platform.runLater(this::loadTasks);
            } catch (Exception ignored) {}
        });
    }

    private void deleteTask(String taskId) {
        workers.submit(() -> {
            try {
                api.deleteTask(taskId);
                Platform.runLater(this::loadTasks);
            } catch (Exception ignored) {}
        });
    }

    @FXML
    private void onRefresh() {
        loadTasks();
    }

    @FXML
    private void onNewTask() {
        var loader = ViewLoader.<CreateTaskDialogController>load("CreateTaskDialog.fxml");
        Stage owner = pendingCol.getScene() != null ? (Stage) pendingCol.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Nueva Tarea Académica", owner);

        loader.controller().init(api, workers, task -> {
            loadTasks();
            UiHelper.showSuccess(owner, "Tarea Registrada",
                    "'" + task.title() + "' ha sido añadida a tu tablero en estado Pendiente.");
        });

        modal.showAndWait();
    }
}
