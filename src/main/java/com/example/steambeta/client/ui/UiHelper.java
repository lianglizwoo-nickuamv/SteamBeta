package com.example.steambeta.client.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utilidades no visuales compartidas para SteamBeta Campus:
 * Proporciona formateo de fechas y configuración de ventanas modales FXML.
 * Toda la interfaz visual se construye 100% mediante FXML y CSS sin diálogos Modena nativos de Windows.
 */
public final class UiHelper {
    public static final String STYLESHEET =
            UiHelper.class.getResource("/com/example/steambeta/client/academic.css") != null
                    ? UiHelper.class.getResource("/com/example/steambeta/client/academic.css").toExternalForm()
                    : "";

    public static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    public static final DateTimeFormatter DATE_SHORT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private UiHelper() {}

    // -------------------------------------------------------------------------
    // 1. UTILIDADES DE FORMATO DE FECHA
    // -------------------------------------------------------------------------

    public static String formatDate(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try {
            return DATE_TIME.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    public static String formatDateShort(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try {
            return DATE_SHORT.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    // -------------------------------------------------------------------------
    // 2. VENTANAS MODALES ESTILIZADAS CON ACADEMIC.CSS (100% FXML)
    // -------------------------------------------------------------------------

    public static Stage createModalStage(Parent root, String title, Stage owner) {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            modal.initOwner(owner);
        }
        modal.setTitle(title);
        Scene scene = new Scene(root);
        scene.setFill(Color.web("#0a0f1d"));
        if (!STYLESHEET.isEmpty()) {
            scene.getStylesheets().add(STYLESHEET);
        }
        modal.setScene(scene);
        return modal;
    }

    public static void showSuccess(Stage owner, String title, String message) {
        var loader = ViewLoader.<MessageDialogController>load("MessageDialog.fxml");
        loader.controller().set("✓", title, message, false);
        var modal = createModalStage(loader.root(), title, owner);
        modal.showAndWait();
    }

    public static void showInformation(Stage owner, String title, String message) {
        var loader = ViewLoader.<MessageDialogController>load("MessageDialog.fxml");
        loader.controller().set("ℹ️", title, message, false);
        var modal = createModalStage(loader.root(), title, owner);
        modal.showAndWait();
    }

    public static void showError(Stage owner, String title, String message) {
        var loader = ViewLoader.<MessageDialogController>load("MessageDialog.fxml");
        loader.controller().set("⚠️", title, message, false);
        var modal = createModalStage(loader.root(), title, owner);
        modal.showAndWait();
    }

    public static boolean showConfirm(Stage owner, String title, String message) {
        var loader = ViewLoader.<MessageDialogController>load("MessageDialog.fxml");
        loader.controller().set("❓", title, message, true);
        var modal = createModalStage(loader.root(), title, owner);
        modal.showAndWait();
        return loader.controller().isConfirmed();
    }

    public static void alert(String title, String message, Alert.AlertType type) {
        String icon = switch (type) {
            case INFORMATION -> "✓";
            case WARNING -> "⚠️";
            case ERROR -> "❌";
            default -> "ℹ️";
        };
        var loader = ViewLoader.<MessageDialogController>load("MessageDialog.fxml");
        loader.controller().set(icon, title, message, false);
        var modal = createModalStage(loader.root(), title, null);
        modal.showAndWait();
    }

    public static boolean confirm(String title, String message) {
        return showConfirm(null, title, message);
    }
}
