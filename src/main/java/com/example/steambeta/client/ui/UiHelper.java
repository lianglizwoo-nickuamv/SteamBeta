package com.example.steambeta.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Utilitarios de UI para componentes modernos de SteamBeta Campus. */
public final class UiHelper {
    public static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    public static final DateTimeFormatter DATE_SHORT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private UiHelper() {}

    public static Label label(String text, String styleClass) {
        var l = new Label(text);
        if (styleClass != null && !styleClass.isBlank()) {
            l.getStyleClass().add(styleClass);
        }
        l.setWrapText(true);
        return l;
    }

    public static Button button(String text, String styleClass) {
        var b = new Button(text);
        if (styleClass != null && !styleClass.isBlank()) {
            b.getStyleClass().add(styleClass);
        }
        return b;
    }

    public static VBox stack(int spacing, Node... nodes) {
        var box = new VBox(spacing, nodes);
        box.setPadding(new Insets(16));
        return box;
    }

    public static VBox card(Node... nodes) {
        var box = new VBox(10, nodes);
        box.getStyleClass().add("card");
        return box;
    }

    public static Label badge(String text, String styleClass) {
        var b = new Label(text);
        b.getStyleClass().add(styleClass);
        return b;
    }

    public static String formatDate(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try {
            return DATE_TIME.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    public static void alert(String title, String message, Alert.AlertType type) {
        var alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static boolean confirm(String title, String message) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    public static Region hSpacer() {
        var r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
