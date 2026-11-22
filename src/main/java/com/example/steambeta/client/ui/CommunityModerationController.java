package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.util.concurrent.ExecutorService;

/**
 * Controlador para la pantalla de moderación de comunidad (FXML).
 */
public final class CommunityModerationController {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label statusLabel;
    @FXML private VBox reportsBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private ApiClient.Community community;
    private MainLayoutController navigator;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers,
                     ApiClient.Community community, MainLayoutController navigator) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.community = community;
        this.navigator = navigator;

        titleLabel.setText("Panel de Moderación: " + community.name());
        subtitleLabel.setText("Supervisión de contenido reportado en " + community.name());
        loadReports();
    }

    private void loadReports() {
        long epoch = scope.generation();
        statusLabel.setText("Cargando reportes pendientes de moderación…");

        workers.submit(() -> {
            try {
                var reports = api.reports(community.id());
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    reportsBox.getChildren().clear();

                    if (reports.isEmpty()) {
                        statusLabel.setText("No hay reportes abiertos pendientes de revisión en esta comunidad.");
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("🛡️", "Sin reportes pendientes",
                                "La comunidad se mantiene en orden y sin incidencias reportadas.", null, null);
                        reportsBox.getChildren().add(empty.root());
                        return;
                    }

                    statusLabel.setText("Mostrando " + reports.size() + " reporte(s) abierto(s):");
                    for (var r : reports) {
                        var card = ViewLoader.<ReportCardController>load("ReportCard.fxml");
                        card.controller().set(r, () -> hideReport(r), () -> dismissReport(r));
                        reportsBox.getChildren().add(card.root());
                    }
                });
            } catch (Exception err) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    statusLabel.setText("No tienes permisos de moderación o no se pudieron cargar los reportes.");
                });
            }
        });
    }

    private void hideReport(ApiClient.Report report) {
        workers.submit(() -> {
            try {
                api.hideReported(community.id(), report.id(), "Contenido ocultado tras moderación.");
                Platform.runLater(this::loadReports);
            } catch (Exception ex) {
                Platform.runLater(() -> UiHelper.alert("Error", "No se pudo ocultar el contenido.", Alert.AlertType.ERROR));
            }
        });
    }

    private void dismissReport(ApiClient.Report report) {
        workers.submit(() -> {
            try {
                api.resolveReport(community.id(), report.id(), "DISMISSED", "Descartado por moderación.");
                Platform.runLater(this::loadReports);
            } catch (Exception ex) {
                Platform.runLater(() -> UiHelper.alert("Error", "No se pudo descartar el reporte.", Alert.AlertType.ERROR));
            }
        });
    }

    @FXML
    private void onBack() {
        if (navigator != null) {
            navigator.openCommunityDetail(community);
        }
    }
}
