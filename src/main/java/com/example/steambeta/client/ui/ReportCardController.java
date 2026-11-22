package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controlador de tarjeta de reporte para moderación de comunidad (FXML).
 */
public final class ReportCardController {

    @FXML private Label reporterLabel;
    @FXML private Label dateLabel;
    @FXML private Label reasonLabel;
    @FXML private Label authorLabel;
    @FXML private Label contentLabel;
    @FXML private Button hideBtn;
    @FXML private Button dismissBtn;

    private Runnable onHideAction;
    private Runnable onDismissAction;

    public void set(ApiClient.Report report, Runnable onHide, Runnable onDismiss) {
        this.onHideAction = onHide;
        this.onDismissAction = onDismiss;

        reporterLabel.setText("Reportado por: " + (report.reporterAlias() != null ? report.reporterAlias() : "Estudiante"));
        dateLabel.setText(UiHelper.formatDate(report.createdAt()));
        reasonLabel.setText("Motivo: " + report.reason());
        authorLabel.setText("Autor del contenido: " + (report.contentAuthorAlias() != null ? report.contentAuthorAlias() : "Anónimo"));
        contentLabel.setText(report.content() == null ? "(Contenido ya eliminado u oculto)" : report.content());
    }

    @FXML
    private void onHide() {
        if (onHideAction != null) onHideAction.run();
    }

    @FXML
    private void onDismiss() {
        if (onDismissAction != null) onDismissAction.run();
    }
}
