package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.EnvironmentConfig;
import com.example.steambeta.client.SessionScope;
import com.example.steambeta.client.ThemeManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Configuración y Diagnóstico (FXML) — SteamBeta Campus:
 * Selección de tema visual, inspección de conectividad, entorno y políticas de seguridad.
 */
public final class SettingsController {

    @FXML private ComboBox<ThemeManager.Theme> themeCombo;
    @FXML private Label envBadge;
    @FXML private Label apiUrlLabel;
    @FXML private Label pingStatusLabel;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;

    @FXML
    public void initialize() {
        if (themeCombo != null) {
            themeCombo.setItems(FXCollections.observableArrayList(
                    ThemeManager.Theme.LIGHT,
                    ThemeManager.Theme.DARK
            ));
            themeCombo.setValue(ThemeManager.getCurrentTheme());
            themeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal != oldVal) {
                    ThemeManager.setTheme(newVal);
                }
            });
        }
    }

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        var env = EnvironmentConfig.getCurrentEnvironment();
        envBadge.setText(env.getDisplayName());
        apiUrlLabel.setText(EnvironmentConfig.getApiUrl());
    }

    @FXML
    private void onPingConnection() {
        pingStatusLabel.setText("Enviando petición de sondeo al servidor Spring Boot…");
        long epoch = scope.generation();
        long t0 = System.currentTimeMillis();

        workers.submit(() -> {
            try {
                var p = api.profile();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    long elapsed = System.currentTimeMillis() - t0;
                    pingStatusLabel.setText("✓ Conexión establecida con éxito en " + elapsed + " ms.\n" +
                            "Servidor respondiendo en: " + EnvironmentConfig.getApiUrl() + "\n" +
                            "Sesión activa de: " + (p.alias() != null ? p.alias() : p.login()));
                    pingStatusLabel.setStyle("-fx-text-fill: #34d399; -fx-font-weight: bold;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    pingStatusLabel.setText("✗ Falló la conexión con el servidor backend: " + e.getMessage());
                    pingStatusLabel.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold;");
                });
            }
        });
    }
}
