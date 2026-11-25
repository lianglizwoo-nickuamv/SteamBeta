package com.example.steambeta.client;

import com.example.steambeta.client.ui.AuthController;
import com.example.steambeta.client.ui.EmptyStateController;
import com.example.steambeta.client.ui.MainLayoutController;
import com.example.steambeta.client.ui.ViewLoader;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ventana Principal de Escritorio (JavaFX + FXML) — SteamBeta Campus:
 * Controlador del Stage y ciclo de vida de la aplicación:
 * - 100% Cero nodos visuales creados desde Java (la jerarquía visual procede íntegramente de FXML).
 * - Carga declarativa de Auth.fxml para inicializar la Scene.
 * - Transición de vistas mediante stage.getScene().setRoot(...) conservando Stage y Scene.
 * - Liberación ordenada de recursos y cierre seguro de sesiones.
 */
public final class AcademicWindow {
    private final Stage stage;
    private final SessionScope scope = new SessionScope();
    private final ExecutorService workers = Executors.newFixedThreadPool(4, r -> {
        var t = new Thread(r, "campus-http");
        t.setDaemon(true);
        return t;
    });

    private ApiClient api;
    private boolean closing;
    private MainLayoutController mainLayoutController;

    public AcademicWindow(Stage stage) {
        this.stage = stage;
    }

    public void open() {
        stage.setTitle("SteamBeta Campus · Ecosistema Digital Universitario");
        stage.setMinWidth(1020);
        stage.setMinHeight(700);
        stage.setOnCloseRequest(event -> {
            event.consume();
            closeWindow();
        });

        showAuthView("", "");
        stage.show();
    }

    private void showAuthView(String notice, String presetLogin) {
        scope.reset();
        var authView = ViewLoader.<AuthController>load("Auth.fxml");
        authView.controller().init(scope, workers, (client, signedIdentity) -> {
            this.api = client;
            showMainLayout();
        }, notice, presetLogin);

        if (stage.getScene() == null) {
            var scene = new Scene(authView.root(), 1240, 820);
            ThemeManager.apply(scene);
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(authView.root());
            ThemeManager.apply(stage.getScene());
        }
    }

    private void showMainLayout() {
        var mainView = ViewLoader.<MainLayoutController>load("MainLayout.fxml");
        this.mainLayoutController = mainView.controller();
        mainLayoutController.init(api, scope, workers, this::logout);
        stage.getScene().setRoot(mainView.root());
    }

    private void logout() {
        var previous = api;
        api = null;
        mainLayoutController = null;
        showAuthView("Sesión cerrada correctamente.", "");

        if (previous != null) {
            workers.submit(() -> {
                try {
                    previous.logout();
                } catch (Exception ignored) {
                    previous.clear();
                }
            });
        }
    }

    private void closeWindow() {
        if (closing) return;
        closing = true;
        scope.reset();
        var previous = api;
        api = null;
        mainLayoutController = null;

        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
        empty.controller().set("🔒", "Cerrando sesión de SteamBeta Campus…", "Liberando recursos y finalizando conexión segura.", null, null);
        if (stage.getScene() != null) {
            stage.getScene().setRoot(empty.root());
            stage.getScene().getRoot().setDisable(true);
        }

        if (previous == null) {
            workers.shutdownNow();
            stage.close();
            return;
        }

        workers.submit(() -> {
            try {
                previous.logout();
            } catch (Exception ignored) {
                previous.clear();
            } finally {
                Platform.runLater(() -> {
                    workers.shutdownNow();
                    stage.close();
                });
            }
        });
    }
}
