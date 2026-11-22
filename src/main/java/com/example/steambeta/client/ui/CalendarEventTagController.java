package com.example.steambeta.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class CalendarEventTagController {

    @FXML private Label tagLabel;

    public void set(String text, String styleClass) {
        tagLabel.setText(text);
        tagLabel.getStyleClass().setAll(styleClass);
    }
}
