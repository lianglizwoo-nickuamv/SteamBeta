package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;

/**
 * Controlador de mensaje/pregunta dentro del espacio unificado de Conversación (FXML).
 * Reutiliza los contratos existentes de Posts, Questions, Comments y acceptSolution sin cambios en BD.
 */
public final class ChatMessageItemController {

    @FXML private HBox messageRow;
    @FXML private StackPane avatarContainer;
    @FXML private Label avatarInitials;
    @FXML private ImageView avatarImage;
    @FXML private VBox messageBubble;

    @FXML private Label authorLabel;
    @FXML private Label ownTag;
    @FXML private Label kindBadge;
    @FXML private Label stateBadge;
    @FXML private Label subjectBadge;
    @FXML private Label dateLabel;

    @FXML private Label titleLabel;
    @FXML private Label bodyLabel;

    @FXML private Button toggleRepliesBtn;
    @FXML private Button deleteBtn;

    @FXML private VBox repliesBox;
    @FXML private Label repliesStatusLabel;
    @FXML private VBox repliesListBox;
    @FXML private TextField replyInputField;
    @FXML private Button sendReplyBtn;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private String communityId;
    private String itemId;
    private boolean isQuestion;
    private boolean isAuthor;
    private Runnable onChanged;

    public void initPost(ApiClient.Post post, String communityId, String avatarUrl,
                         ApiClient api, SessionScope scope, ExecutorService workers, Runnable onChanged) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.communityId = communityId;
        this.itemId = post.id();
        this.isQuestion = false;
        this.onChanged = onChanged;

        String author = safeAlias(post.authorAlias());
        this.isAuthor = isCurrentUser(post.authorAccountId());

        applyCommonHeader(author, post.authorAccountId(), post.createdAt(), avatarUrl);
        applyBubbleStyle(false);

        String title = post.title() != null ? post.title().strip() : "";
        String body = post.body() != null ? post.body().strip() : "";

        // En mensajes rápidos del chat el título se deriva del inicio del mensaje: evitamos duplicarlo visualmente.
        boolean showTitle = !title.isEmpty() && !isDerivedTitle(title, body);
        titleLabel.setVisible(showTitle);
        titleLabel.setManaged(showTitle);
        if (showTitle) {
            titleLabel.setText(title);
        }
        bodyLabel.setText(body.isEmpty() ? title : body);

        updateRepliesButtonLabel(post.commentCount());
        replyInputField.setPromptText("Responder o comentar este mensaje…");

        deleteBtn.setVisible(isAuthor);
        deleteBtn.setManaged(isAuthor);
    }

    public void initQuestion(ApiClient.Question q, String communityId, String avatarUrl,
                             ApiClient api, SessionScope scope, ExecutorService workers, Runnable onChanged) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.communityId = communityId;
        this.itemId = q.id();
        this.isQuestion = true;
        this.onChanged = onChanged;

        String author = safeAlias(q.authorAlias());
        this.isAuthor = isCurrentUser(q.authorAccountId());

        applyCommonHeader(author, q.authorAccountId(), q.createdAt(), avatarUrl);
        applyBubbleStyle(true);

        kindBadge.setText("PREGUNTA ACADÉMICA");
        kindBadge.setVisible(true);
        kindBadge.setManaged(true);

        stateBadge.getStyleClass().removeAll("badge-resolved", "badge-pending");
        if (q.isResolved()) {
            stateBadge.setText("✓ RESUELTA");
            stateBadge.getStyleClass().add("badge-resolved");
        } else {
            stateBadge.setText("PENDIENTE");
            stateBadge.getStyleClass().add("badge-pending");
        }
        stateBadge.setVisible(true);
        stateBadge.setManaged(true);

        if (q.subjectTag() != null && !q.subjectTag().isBlank()) {
            subjectBadge.setText("Materia: " + q.subjectTag().strip());
            subjectBadge.setVisible(true);
            subjectBadge.setManaged(true);
        }

        titleLabel.setText(q.title() != null ? q.title() : "");
        titleLabel.setVisible(true);
        titleLabel.setManaged(true);
        bodyLabel.setText(q.body() != null ? q.body() : "");

        updateRepliesButtonLabel(q.commentCount());
        replyInputField.setPromptText("Escribe una respuesta académica para esta pregunta…");

        deleteBtn.setVisible(false);
        deleteBtn.setManaged(false);
    }

    private void applyCommonHeader(String author, String authorAccountId, String createdAt, String avatarUrl) {
        authorLabel.setText(author);
        dateLabel.setText(UiHelper.formatDate(createdAt));
        ownTag.setVisible(isAuthor);
        ownTag.setManaged(isAuthor);

        AvatarLoader.load(avatarImage, avatarInitials, author, authorAccountId, avatarUrl, 38, api, workers);
    }

    private void applyBubbleStyle(boolean question) {
        messageBubble.getStyleClass().removeAll("chat-bubble-own", "chat-bubble-peer", "chat-bubble-question");
        if (question) {
            messageBubble.getStyleClass().add("chat-bubble-question");
        } else if (isAuthor) {
            messageBubble.getStyleClass().add("chat-bubble-own");
        } else {
            messageBubble.getStyleClass().add("chat-bubble-peer");
        }
    }

    private void updateRepliesButtonLabel(long count) {
        if (isQuestion) {
            toggleRepliesBtn.setText(count == 1 ? "Ver 1 respuesta" : "Ver respuestas (" + count + ")");
        } else {
            toggleRepliesBtn.setText(count == 1 ? "1 respuesta" : "Respuestas (" + count + ")");
        }
    }

    private boolean isCurrentUser(String authorAccountId) {
        var profile = api != null ? api.getCachedProfile() : null;
        return profile != null && profile.accountId() != null && profile.accountId().equals(authorAccountId);
    }

    private static boolean isDerivedTitle(String title, String body) {
        if (body.isEmpty()) return true;
        if (body.equals(title)) return true;
        String cleanTitle = title.endsWith("…") ? title.substring(0, title.length() - 1) : title;
        return body.startsWith(cleanTitle) && cleanTitle.length() >= 3;
    }

    private static String safeAlias(String alias) {
        return (alias != null && !alias.isBlank()) ? alias : "Estudiante";
    }

    @FXML
    private void onToggleReplies() {
        boolean show = !repliesBox.isVisible();
        repliesBox.setVisible(show);
        repliesBox.setManaged(show);
        if (show) {
            loadReplies();
        }
    }

    private void loadReplies() {
        long epoch = scope.generation();
        repliesStatusLabel.setText("Cargando respuestas…");
        repliesStatusLabel.setVisible(true);
        repliesStatusLabel.setManaged(true);

        workers.submit(() -> {
            try {
                var comments = api.comments(communityId, itemId);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    repliesListBox.getChildren().clear();
                    repliesStatusLabel.setVisible(false);
                    repliesStatusLabel.setManaged(false);

                    if (comments.isEmpty()) {
                        repliesStatusLabel.setText(isQuestion
                                ? "Aún no hay respuestas para esta pregunta. Sé el primero en ayudar."
                                : "Aún no hay respuestas en este hilo.");
                        repliesStatusLabel.setVisible(true);
                        repliesStatusLabel.setManaged(true);
                    } else {
                        for (var c : comments) {
                            var item = ViewLoader.<CommentItemController>load("CommentItem.fxml");
                            boolean canAccept = isQuestion && isAuthor && !c.isSolution();
                            item.controller().set(c, canAccept, () -> acceptSolution(c.id()));
                            repliesListBox.getChildren().add(item.root());
                        }
                    }
                    updateRepliesButtonLabel(comments.size());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    repliesStatusLabel.setText("No se pudieron cargar las respuestas.");
                    repliesStatusLabel.setVisible(true);
                    repliesStatusLabel.setManaged(true);
                });
            }
        });
    }

    @FXML
    private void onSendReply() {
        String text = replyInputField.getText().strip();
        if (text.isEmpty() || text.length() > 2000) return;

        replyInputField.setDisable(true);
        sendReplyBtn.setDisable(true);
        workers.submit(() -> {
            try {
                api.comment(communityId, itemId, text);
                Platform.runLater(() -> {
                    replyInputField.clear();
                    replyInputField.setDisable(false);
                    sendReplyBtn.setDisable(false);
                    loadReplies();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    replyInputField.setDisable(false);
                    sendReplyBtn.setDisable(false);
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "Error al comunicarse con el servidor.";
                    UiHelper.alert("Error", "No se pudo enviar la respuesta: " + msg, Alert.AlertType.ERROR);
                });
            }
        });
    }

    private void acceptSolution(String commentId) {
        workers.submit(() -> {
            try {
                api.acceptSolution(communityId, itemId, commentId);
                Platform.runLater(() -> {
                    stateBadge.getStyleClass().removeAll("badge-resolved", "badge-pending");
                    stateBadge.setText("✓ RESUELTA");
                    stateBadge.getStyleClass().add("badge-resolved");
                    loadReplies();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "No se pudo marcar la solución.";
                    UiHelper.alert("Error", msg, Alert.AlertType.ERROR);
                });
            }
        });
    }

    @FXML
    private void onDelete() {
        if (isQuestion) return;
        if (!UiHelper.confirm("Eliminar mensaje", "¿Estás seguro de que deseas eliminar este mensaje?")) return;

        workers.submit(() -> {
            try {
                api.deletePost(communityId, itemId);
                Platform.runLater(() -> {
                    if (onChanged != null) onChanged.run();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "Error de permisos o falla en el servidor.";
                    UiHelper.alert("Error al eliminar", "No se pudo eliminar: " + msg, Alert.AlertType.ERROR);
                });
            }
        });
    }
}
