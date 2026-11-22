package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Biblioteca Universitaria (FXML) — SteamBeta Campus:
 * Carga de asignaturas matriculadas y desglose de actividades académicas.
 * 100% basado en FXML sin construcción imperativa de interfaces.
 */
public final class AcademicLibraryController {

    @FXML private ListView<ApiClient.Subject> subjectsList;
    @FXML private Label libraryStatus;
    @FXML private VBox detailPane;
    @FXML private Label detailStatusLabel;
    @FXML private VBox detailContentBox;
    @FXML private Label subjectCodeLabel;
    @FXML private Label subjectNameLabel;
    @FXML private Label subjectMetaLabel;
    @FXML private Label progressPercentLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressCountsLabel;
    @FXML private VBox activitiesListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        setupList();
        loadLibrary();
    }

    private void setupList() {
        subjectsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ApiClient.Subject subject, boolean empty) {
                super.updateItem(subject, empty);
                setText(null);
                if (empty || subject == null) {
                    setGraphic(null);
                    return;
                }
                var cell = ViewLoader.<SubjectCellController>load("SubjectCell.fxml");
                cell.controller().set(subject);
                setGraphic(cell.root());
            }
        });

        subjectsList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                loadSubjectDetail(selected);
            }
        });
    }

    private void loadLibrary() {
        long epoch = scope.generation();
        libraryStatus.setText("Consultando matrícula en el servidor central…");

        workers.submit(() -> {
            try {
                var rows = api.library();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    subjectsList.getItems().setAll(rows);
                    if (rows.isEmpty()) {
                        libraryStatus.setText("Sin materias matriculadas.");
                        detailStatusLabel.setVisible(false);
                        detailStatusLabel.setManaged(false);
                        detailContentBox.setVisible(false);
                        detailContentBox.setManaged(false);
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("🎓", "No se registran materias matriculadas",
                                "Tu cuenta no tiene inscripciones activas registradas en el sistema institucional.", null, null);
                        detailPane.getChildren().setAll(empty.root());
                    } else {
                        libraryStatus.setText(rows.size() + " materias matriculadas");
                        subjectsList.getSelectionModel().selectFirst();
                    }
                });
            } catch (Exception err) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    libraryStatus.setText("Biblioteca institucional no vinculada o sin conexión.");
                    detailStatusLabel.setVisible(false);
                    detailStatusLabel.setManaged(false);
                    detailContentBox.setVisible(false);
                    detailContentBox.setManaged(false);
                    var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                    empty.controller().set("🎓", "Sin matrícula institucional vinculada",
                                "Tu cuenta no tiene inscripciones académicas activas en el sistema institucional. " +
                                "Puedes explorar recursos en las comunidades académicas que hayas unido.", null, null);
                    detailPane.getChildren().setAll(empty.root());
                });
            }
        });
    }

    private void loadSubjectDetail(ApiClient.Subject subject) {
        long epoch = scope.generation();
        detailStatusLabel.setVisible(true);
        detailStatusLabel.setManaged(true);
        detailStatusLabel.setText("Cargando actividades de " + subject.name() + "…");
        detailContentBox.setVisible(false);
        detailContentBox.setManaged(false);

        workers.submit(() -> {
            try {
                var data = api.detail(subject.offeringId());
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    var summary = data.summary();
                    String percent = summary.progressPercent() == null ? "Sin requisitos"
                            : summary.progressPercent().stripTrailingZeros().toPlainString() + "%";
                    double progressVal = summary.progressPercent() == null ? 0 : summary.progressPercent().doubleValue() / 100;

                    subjectCodeLabel.setText(summary.code());
                    subjectNameLabel.setText(summary.name());
                    subjectMetaLabel.setText("Período: " + summary.termCode() + " · Sección: " + summary.sectionCode());

                    progressPercentLabel.setText(percent);
                    progressBar.setProgress(progressVal);
                    progressCountsLabel.setText(summary.completedCount() + " de " + summary.requiredCount() + " actividades obligatorias completadas");

                    activitiesListBox.getChildren().clear();
                    for (var act : data.activities()) {
                        var actCard = ViewLoader.<ActivityCardController>load("ActivityCard.fxml");
                        actCard.controller().set(act);
                        activitiesListBox.getChildren().add(actCard.root());
                    }

                    detailStatusLabel.setVisible(false);
                    detailStatusLabel.setManaged(false);
                    detailContentBox.setVisible(true);
                    detailContentBox.setManaged(true);
                });
            } catch (Exception err) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    detailStatusLabel.setVisible(true);
                    detailStatusLabel.setManaged(true);
                    detailStatusLabel.setText("No se pudo cargar el detalle de esta materia.");
                    detailContentBox.setVisible(false);
                    detailContentBox.setManaged(false);
                });
            }
        });
    }

    @FXML
    private void onRefresh() {
        loadLibrary();
    }
}
