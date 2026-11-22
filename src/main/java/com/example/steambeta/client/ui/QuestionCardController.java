package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controlador de Tarjeta de Pregunta Académica (FXML) — SteamBeta Campus.
 */
public final class QuestionCardController {

    @FXML private Label stateBadge;
    @FXML private Label subjectBadge;
    @FXML private Label authorDateLabel;
    @FXML private Label titleLabel;
    @FXML private Label bodyLabel;
    @FXML private Label repliesBadge;
    @FXML private Button openBtn;

    private Runnable onOpen;

    public void set(ApiClient.Question q, Runnable onOpen) {
        this.onOpen = onOpen;

        stateBadge.getStyleClass().removeAll("badge-resolved", "badge-pending");
        if (q.isResolved()) {
            stateBadge.setText("✓ RESUELTA CON SOLUCIÓN");
            stateBadge.getStyleClass().add("badge-resolved");
        } else {
            stateBadge.setText("⏳ PENDIENTE DE RESPUESTA");
            stateBadge.getStyleClass().add("badge-pending");
        }

        if (q.subjectTag() != null && !q.subjectTag().isBlank()) {
            subjectBadge.setText("Materia: " + q.subjectTag());
            subjectBadge.setVisible(true);
            subjectBadge.setManaged(true);
        } else {
            subjectBadge.setVisible(false);
            subjectBadge.setManaged(false);
        }

        authorDateLabel.setText("Por: " + q.authorAlias() + " · " + UiHelper.formatDate(q.createdAt()));
        titleLabel.setText(q.title());
        bodyLabel.setText(q.body() != null ? q.body() : "");
        repliesBadge.setText(q.commentCount() + " respuestas / aportes");
    }

    @FXML
    private void onOpen() {
        if (onOpen != null) {
            onOpen.run();
        }
    }
}
