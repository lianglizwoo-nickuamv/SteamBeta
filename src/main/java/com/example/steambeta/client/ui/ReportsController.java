package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Controlador del Centro de Reportes Personales («Mis reportes», FXML) — SteamBeta Campus.
 */
public final class ReportsController {

    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Button btnClearDates;
    @FXML private ComboBox<String> formatCombo;
    @FXML private Label statusLabel;

    @FXML private Button btnGenActivity;
    @FXML private Button btnGenCommunities;
    @FXML private Button btnGenPlanning;
    @FXML private Button btnGenExchange;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;

    @FXML
    public void initialize() {
        formatCombo.getItems().setAll("PDF (.pdf)", "Excel (.xlsx)");
        formatCombo.getSelectionModel().selectFirst();
    }

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
    }

    @FXML
    private void onClearDates() {
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        setStatus("Rango restablecido a histórico completo.", false);
    }

    @FXML private void onGenerateActivity() { generate("academic-activity", "mi_actividad_academica", "Mi actividad académica"); }
    @FXML private void onGenerateCommunities() { generate("communities", "mis_comunidades", "Mis comunidades"); }
    @FXML private void onGeneratePlanning() { generate("academic-planning", "mi_planificacion_academica", "Mi planificación académica"); }
    @FXML private void onGenerateExchange() { generate("exchange-publications", "mis_publicaciones_intercambio", "Mis publicaciones de intercambio"); }

    private void generate(String reportKey, String filePrefix, String displayName) {
        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            setStatus("La fecha inicial no puede ser posterior a la fecha final.", true);
            return;
        }

        boolean isXlsx = formatCombo.getSelectionModel().getSelectedIndex() == 1;
        String format = isXlsx ? "XLSX" : "PDF";
        String ext = isXlsx ? ".xlsx" : ".pdf";

        Stage owner = statusLabel.getScene() != null ? (Stage) statusLabel.getScene().getWindow() : null;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar reporte: " + displayName);
        chooser.setInitialFileName("steambeta_" + filePrefix + "_" + LocalDate.now().format(FILE_DATE_FMT) + ext);
        if (isXlsx) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Libro de Excel (*.xlsx)", "*.xlsx"));
        } else {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));
        }

        File target = chooser.showSaveDialog(owner);
        if (target == null) return;

        long epoch = scope.generation();
        setBusy(true);
        setStatus("Generando «" + displayName + "» en formato " + format + "…", false);

        String fromIso = from != null ? from.toString() : null;
        String toIso = to != null ? to.toString() : null;

        workers.submit(() -> {
            try {
                byte[] bytes = api.downloadPersonalReport(reportKey, format, fromIso, toIso);
                Files.write(target.toPath(), bytes);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    setBusy(false);
                    setStatus("Reporte guardado correctamente: " + target.getName() + " (" + UiHelper.formatFileSize(bytes.length) + ").", false);
                    UiHelper.showSuccess(owner, "Reporte generado",
                            "El reporte «" + displayName + "» se guardó en:\n" + target.getAbsolutePath());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    setBusy(false);
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "Error al generar el reporte en el servidor.";
                    setStatus("No se pudo generar el reporte: " + msg, true);
                    UiHelper.showError(owner, "Error al generar reporte", msg);
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        for (Button b : List.of(btnGenActivity, btnGenCommunities, btnGenPlanning, btnGenExchange, btnClearDates)) {
            if (b != null) b.setDisable(busy);
        }
        if (fromDatePicker != null) fromDatePicker.setDisable(busy);
        if (toDatePicker != null) toDatePicker.setDisable(busy);
        if (formatCombo != null) formatCombo.setDisable(busy);
    }

    private void setStatus(String text, boolean error) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("muted", "status-error", "status-success");
        statusLabel.getStyleClass().add(error ? "status-error" : "muted");
    }
}
