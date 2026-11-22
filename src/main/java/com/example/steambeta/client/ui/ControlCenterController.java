package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Control Center (FXML) — SteamBeta Campus:
 * Supervisión y sanciones de moderación global autorizada.
 */
public final class ControlCenterController {

    @FXML private Button btnStudents;
    @FXML private Button btnAudit;
    @FXML private HBox searchBar;
    @FXML private TextField studentQueryField;
    @FXML private Label statusLabel;
    @FXML private VBox subContentBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private String currentSubTab = "STUDENTS";

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        loadStudents("");
    }

    private void loadStudents(String query) {
        long epoch = scope.generation();
        statusLabel.setText("Consultando ficha de estudiantes…");

        workers.submit(() -> {
            try {
                var list = api.controlCenterSearchStudents(query);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    subContentBox.getChildren().clear();

                    if (list.isEmpty()) {
                        statusLabel.setText("No se encontraron estudiantes para la búsqueda.");
                    } else {
                        statusLabel.setText("Mostrando " + list.size() + " estudiante(s):");
                        for (var s : list) {
                            var card = ViewLoader.<StudentCardController>load("StudentCard.fxml");
                            card.controller().set(s);
                            subContentBox.getChildren().add(card.root());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error al consultar estudiantes."));
            }
        });
    }

    private void loadAuditLogs() {
        long epoch = scope.generation();
        statusLabel.setText("Consultando bitácora inmutable de auditoría…");

        workers.submit(() -> {
            try {
                var logs = api.controlCenterAuditLogs();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    subContentBox.getChildren().clear();

                    if (logs.isEmpty()) {
                        statusLabel.setText("No hay registros en la bitácora.");
                    } else {
                        statusLabel.setText("Mostrando últimos " + logs.size() + " evento(s) de auditoría:");
                        for (var log : logs) {
                            var card = ViewLoader.<AuditCardController>load("AuditCard.fxml");
                            card.controller().set(log);
                            subContentBox.getChildren().add(card.root());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error al consultar bitácora."));
            }
        });
    }

    @FXML
    private void onTabStudents() {
        currentSubTab = "STUDENTS";
        setTabActive(btnStudents);
        searchBar.setVisible(true);
        searchBar.setManaged(true);
        loadStudents(studentQueryField.getText().strip());
    }

    @FXML
    private void onTabAudit() {
        currentSubTab = "AUDIT";
        setTabActive(btnAudit);
        searchBar.setVisible(false);
        searchBar.setManaged(false);
        loadAuditLogs();
    }

    private void setTabActive(Button activeBtn) {
        for (Button b : List.of(btnStudents, btnAudit)) {
            b.getStyleClass().removeAll("tab-btn", "tab-btn-active");
            if (b == activeBtn) {
                b.getStyleClass().add("tab-btn-active");
            } else {
                b.getStyleClass().add("tab-btn");
            }
        }
    }

    @FXML
    private void onSearchStudent() {
        if ("STUDENTS".equals(currentSubTab)) {
            loadStudents(studentQueryField.getText().strip());
        }
    }

    @FXML
    private void onRefresh() {
        if ("STUDENTS".equals(currentSubTab)) {
            loadStudents(studentQueryField.getText().strip());
        } else {
            loadAuditLogs();
        }
    }
}
