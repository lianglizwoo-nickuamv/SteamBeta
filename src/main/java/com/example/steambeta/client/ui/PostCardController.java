package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Tarjeta de Publicación (FXML) — SteamBeta Campus:
 * Maneja visualización de posts, comentarios interactivos, borrado y reportes.
 * Sin TextInputDialog ni Alert nativos.
 */
public final class PostCardController {

    @FXML private Label titleLabel;
    @FXML private Label authorDateLabel;
    @FXML private Label bodyLabel;
    @FXML private Button toggleCommentsBtn;
    @FXML private Button deleteBtn;

    @FXML private VBox commentsBox;
    @FXML private VBox commentsListBox;
    @FXML private TextField commentInputField;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private ApiClient.Post post;
    private String communityId;
    private Runnable onPostDeleted;

    public void init(ApiClient.Post post, String communityId, ApiClient api, SessionScope scope,
                     ExecutorService workers, Runnable onPostDeleted) {
        this.post = post;
        this.communityId = communityId;
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.onPostDeleted = onPostDeleted;

        titleLabel.setText(post.title());
        authorDateLabel.setText(post.authorAlias() + " · " + UiHelper.formatDate(post.createdAt()));
        bodyLabel.setText(post.body() != null ? post.body() : "");
        toggleCommentsBtn.setText("💬 Comentarios (" + post.commentCount() + ")");

        var profile = api.getCachedProfile();
        boolean isAuthor = profile != null && profile.accountId() != null && profile.accountId().equals(post.authorAccountId());

        if (isAuthor) {
            deleteBtn.setVisible(true);
            deleteBtn.setManaged(true);
        }
    }

    @FXML
    private void onToggleComments() {
        boolean show = !commentsBox.isVisible();
        commentsBox.setVisible(show);
        commentsBox.setManaged(show);
        if (show) {
            loadComments();
        }
    }

    private void loadComments() {
        long epoch = scope.generation();
        workers.submit(() -> {
            try {
                var comments = api.comments(communityId, post.id());
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    commentsListBox.getChildren().clear();

                    if (comments.isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("💬", "Aún no hay comentarios", "Sé el primero en aportar una respuesta o comentario a esta publicación.", null, null);
                        commentsListBox.getChildren().add(empty.root());
                    } else {
                        for (var c : comments) {
                            var item = ViewLoader.<CommentItemController>load("CommentItem.fxml");
                            item.controller().set(c);
                            commentsListBox.getChildren().add(item.root());
                        }
                    }
                    toggleCommentsBtn.setText("💬 Comentarios (" + comments.size() + ")");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    commentsListBox.getChildren().clear();
                    var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "Error al consultar comentarios en el servidor.";
                    empty.controller().set("✕", "No se pudieron cargar los comentarios", msg, "Reintentar", this::loadComments);
                    commentsListBox.getChildren().add(empty.root());
                });
            }
        });
    }

    @FXML
    private void onSendComment() {
        String text = commentInputField.getText().strip();
        if (text.isEmpty() || text.length() > 2000) return;

        commentInputField.setDisable(true);
        workers.submit(() -> {
            try {
                api.comment(communityId, post.id(), text);
                Platform.runLater(() -> {
                    commentInputField.clear();
                    commentInputField.setDisable(false);
                    loadComments();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    commentInputField.setDisable(false);
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "Error al comunicarse con el servidor.";
                    UiHelper.alert("Error", "No se pudo enviar el comentario: " + msg, Alert.AlertType.ERROR);
                });
            }
        });
    }

    @FXML
    private void onDeletePost() {
        if (!UiHelper.confirm("Eliminar publicación", "¿Estás seguro de que deseas eliminar esta publicación?")) return;

        workers.submit(() -> {
            try {
                api.deletePost(communityId, post.id());
                Platform.runLater(() -> {
                    if (onPostDeleted != null) onPostDeleted.run();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage()
                            : "Error de permisos o falla en el servidor.";
                    UiHelper.alert("Error al eliminar", "No se pudo eliminar la publicación: " + msg, Alert.AlertType.ERROR);
                });
            }
        });
    }
}
