package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Controlador para elemento de comentario o respuesta en hilo (FXML).
 */
public final class CommentItemController {

    @FXML private VBox commentRoot;
    @FXML private Label authorLabel;
    @FXML private Label solutionBadge;
    @FXML private Label dateLabel;
    @FXML private Label bodyLabel;
    @FXML private HBox solutionActionRow;
    @FXML private Button btnAcceptSolution;

    private Runnable onAcceptSolution;

    public void set(ApiClient.Comment c) {
        set(c, false, null);
    }

    public void set(ApiClient.Comment c, boolean canAcceptSolution, Runnable onAcceptSolution) {
        this.onAcceptSolution = onAcceptSolution;
        authorLabel.setText(c.authorAlias() != null ? c.authorAlias() : "Estudiante");
        dateLabel.setText(UiHelper.formatDate(c.createdAt()));
        bodyLabel.setText(c.body() != null ? c.body() : "");

        if (commentRoot != null) {
            commentRoot.getStyleClass().remove("comment-solution");
            if (c.isSolution()) {
                commentRoot.getStyleClass().add("comment-solution");
            }
        }
        if (solutionBadge != null) {
            solutionBadge.setVisible(c.isSolution());
            solutionBadge.setManaged(c.isSolution());
        }
        if (solutionActionRow != null) {
            boolean showAction = canAcceptSolution && !c.isSolution() && onAcceptSolution != null;
            solutionActionRow.setVisible(showAction);
            solutionActionRow.setManaged(showAction);
        }
    }

    @FXML
    private void onAcceptSolution() {
        if (onAcceptSolution != null) {
            onAcceptSolution.run();
        }
    }
}
