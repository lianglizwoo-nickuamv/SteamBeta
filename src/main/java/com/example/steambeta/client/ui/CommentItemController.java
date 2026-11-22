package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador para elemento de comentario (FXML).
 */
public final class CommentItemController {

    @FXML private Label authorLabel;
    @FXML private Label dateLabel;
    @FXML private Label bodyLabel;

    public void set(ApiClient.Comment c) {
        authorLabel.setText(c.authorAlias() != null ? c.authorAlias() : "Estudiante");
        dateLabel.setText(UiHelper.formatDate(c.createdAt()));
        bodyLabel.setText(c.body() != null ? c.body() : "");
    }
}
