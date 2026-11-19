package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Vista Integral de Comunidad para SteamBeta Campus:
 * Soporta Pestañas de Publicaciones, Preguntas y Respuestas (Q&A),
 * Biblioteca de Recursos, Calendario de Actividades, e Integrantes y Administración.
 */
public final class CommunityDetailComponent {

    public interface CommunityNavigationListener {
        void onBackToHome();
        void onOpenModeration(ApiClient.Community community);
    }

    private final BorderPane root;
    private final SessionScope scope;
    private final ExecutorService workers;
    private final ApiClient api;
    private final ApiClient.Community community;
    private final CommunityNavigationListener navigation;

    private String activeTab = "POSTS"; // POSTS, QA, RESOURCES, CALENDAR, MEMBERS
    private VBox mainContainer;
    private HBox tabHeader;

    public CommunityDetailComponent(BorderPane root, SessionScope scope, ExecutorService workers,
                                    ApiClient api, ApiClient.Community community,
                                    CommunityNavigationListener navigation) {
        this.root = root;
        this.scope = scope;
        this.workers = workers;
        this.api = api;
        this.community = community;
        this.navigation = navigation;
    }

    public void show() {
        long epoch = scope.reset();
        root.setBottom(null);

        // Cabecera superior de la comunidad
        var backBtn = UiHelper.button("← Mis Comunidades", "secondary");
        backBtn.setOnAction(e -> navigation.onBackToHome());

        var roleBadge = UiHelper.badge("Rol: " + community.role(),
                "ADMIN".equals(community.role()) ? "badge-admin" :
                "MODERATOR".equals(community.role()) ? "badge-mod" : "badge-role");

        var titleBox = new HBox(12,
                UiHelper.label(community.name(), "title"),
                roleBadge
        );
        titleBox.setAlignment(Pos.CENTER_LEFT);

        var descLabel = UiHelper.label(community.description(), "muted");
        var rulesLabel = UiHelper.label("Reglas: " + (community.rules() != null ? community.rules() : "Respeto mutuo."), "muted");
        rulesLabel.setStyle("-fx-font-style: italic; -fx-font-size: 12px;");

        // Botones de pestañas de navegación
        var btnPosts = UiHelper.button("💬 Conversaciones", "tab-btn");
        var btnQA = UiHelper.button("❓ Dudas y Soluciones (Q&A)", "tab-btn");
        var btnResources = UiHelper.button("📚 Biblioteca de Recursos", "tab-btn");
        var btnCalendar = UiHelper.button("📅 Calendario y Exámenes", "tab-btn");
        var btnMembers = UiHelper.button("👥 Integrantes y Gestión", "tab-btn");

        tabHeader = new HBox(8, btnPosts, btnQA, btnResources, btnCalendar, btnMembers);
        tabHeader.setPadding(new Insets(10, 0, 10, 0));

        if ("MODERATOR".equals(community.role()) || "ADMIN".equals(community.role())) {
            var modBtn = UiHelper.button("🛡️ Panel de Moderación", "secondary");
            modBtn.setOnAction(e -> navigation.onOpenModeration(community));
            tabHeader.getChildren().add(modBtn);
        }

        btnPosts.setOnAction(e -> switchTab("POSTS", epoch));
        btnQA.setOnAction(e -> switchTab("QA", epoch));
        btnResources.setOnAction(e -> switchTab("RESOURCES", epoch));
        btnCalendar.setOnAction(e -> switchTab("CALENDAR", epoch));
        btnMembers.setOnAction(e -> switchTab("MEMBERS", epoch));

        mainContainer = new VBox(14);
        var scroll = new ScrollPane(new VBox(12,
                backBtn,
                titleBox,
                descLabel,
                rulesLabel,
                tabHeader,
                new Separator(),
                mainContainer
        ));
        scroll.setFitToWidth(true);
        root.setCenter(scroll);

        switchTab("POSTS", epoch);
    }

    private void switchTab(String tab, long epoch) {
        this.activeTab = tab;
        root.setBottom(null);
        mainContainer.getChildren().clear();

        // Actualizar estilos activos de pestañas
        int idx = 0;
        for (Node node : tabHeader.getChildren()) {
            if (node instanceof Button btn && !btn.getText().contains("Moderación")) {
                btn.getStyleClass().removeAll("tab-btn-active");
                if ((idx == 0 && "POSTS".equals(tab)) ||
                    (idx == 1 && "QA".equals(tab)) ||
                    (idx == 2 && "RESOURCES".equals(tab)) ||
                    (idx == 3 && "CALENDAR".equals(tab)) ||
                    (idx == 4 && "MEMBERS".equals(tab))) {
                    btn.getStyleClass().add("tab-btn-active");
                }
                idx++;
            }
        }

        switch (tab) {
            case "POSTS" -> renderPostsTab(epoch);
            case "QA" -> renderQaTab(epoch);
            case "RESOURCES" -> renderResourcesTab(epoch);
            case "CALENDAR" -> renderCalendarTab(epoch);
            case "MEMBERS" -> renderMembersTab(epoch);
        }
    }

    // =========================================================================
    // Pestaña 1: Conversaciones y Publicaciones
    // =========================================================================

    private void renderPostsTab(long epoch) {
        var postList = new VBox(14);
        var statusLabel = UiHelper.label("Cargando publicaciones…", "muted");
        var moreBtn = UiHelper.button("Más antiguas", "secondary");
        moreBtn.setVisible(false);

        // Editor de publicación abajo
        var titleField = new TextField();
        titleField.setPromptText("Título del tema o pregunta (3–160 caracteres)");
        var bodyArea = new TextArea();
        bodyArea.setPromptText("Explica tu duda o comentario con detalle…");
        bodyArea.setWrapText(true);
        bodyArea.setPrefRowCount(3);

        var publishBtn = UiHelper.button("Publicar en la comunidad", "primary");
        var publishStatus = UiHelper.label("", "error");

        var composer = new VBox(8,
                titleField,
                bodyArea,
                new HBox(12, publishBtn, publishStatus)
        );
        composer.setPadding(new Insets(12, 24, 16, 24));
        composer.getStyleClass().add("bottom-bar");
        root.setBottom(composer);

        publishBtn.setOnAction(e -> {
            String title = titleField.getText().strip();
            String body = bodyArea.getText().strip();
            if (title.length() < 3 || title.length() > 160 || body.isEmpty() || body.length() > 10000) {
                publishStatus.setText("Revisa el título (3–160) y contenido (hasta 10 000 caracteres).");
                return;
            }

            titleField.setDisable(true);
            bodyArea.setDisable(true);
            publishBtn.setDisable(true);
            publishStatus.setText("Publicando…");

            execute(() -> api.createPost(community.id(), UUID.randomUUID(), title, body), post -> {
                titleField.clear();
                bodyArea.clear();
                titleField.setDisable(false);
                bodyArea.setDisable(false);
                publishBtn.setDisable(false);
                publishStatus.setText("");
                switchTab("POSTS", epoch);
            }, err -> {
                titleField.setDisable(false);
                bodyArea.setDisable(false);
                publishBtn.setDisable(false);
                publishStatus.setText("No se pudo publicar. Revisa la conexión.");
            });
        });

        mainContainer.getChildren().setAll(
                UiHelper.label("Conversaciones Recientes", "section-title"),
                statusLabel,
                postList,
                moreBtn
        );

        loadPostsPage(null, null, postList, moreBtn, statusLabel, epoch);
    }

    private void loadPostsPage(String beforeTime, String beforeId, VBox list, Button moreBtn, Label status, long epoch) {
        moreBtn.setDisable(true);
        execute(() -> api.posts(community.id(), beforeTime, beforeId), page -> {
            if (!scope.current(epoch)) return;
            if (beforeTime == null) list.getChildren().clear();

            if (page.posts().isEmpty() && beforeTime == null) {
                status.setText("Aún no hay publicaciones en esta comunidad. ¡Sé el primero en compartir algo!");
            } else {
                status.setText("Publicaciones ordenadas de más recientes a más antiguas.");
            }

            for (var post : page.posts()) {
                list.getChildren().add(buildPostCard(post, epoch));
            }

            moreBtn.setDisable(false);
            moreBtn.setVisible(page.nextBeforeTime() != null);
            moreBtn.setOnAction(e -> loadPostsPage(page.nextBeforeTime(), page.nextBeforeId(), list, moreBtn, status, epoch));
        }, err -> {
            if (!scope.current(epoch)) return;
            status.setText("Error al cargar las publicaciones.");
        });
    }

    private VBox buildPostCard(ApiClient.Post post, long epoch) {
        var card = UiHelper.card();
        var dateFormatted = UiHelper.formatDate(post.createdAt());

        var header = new HBox(8,
                UiHelper.label(post.title(), "card-title"),
                UiHelper.hSpacer(),
                UiHelper.label(post.authorAlias() + " · " + dateFormatted, "muted")
        );
        header.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().add(header);

        if (post.body() != null && !post.body().isBlank()) {
            card.getChildren().add(UiHelper.label(post.body(), "post-body"));
        } else if ("DELETED".equals(post.status())) {
            card.getChildren().add(UiHelper.label("(Esta publicación ha sido eliminada por su autor)", "muted"));
        }

        // Acciones sobre el post
        var actions = new HBox(8);
        boolean isAuthor = post.authorAccountId().equals(api.accountId());

        if (isAuthor && "ACTIVE".equals(post.status())) {
            var editBtn = UiHelper.button("Editar", "secondary");
            editBtn.setOnAction(e -> editPostDialog(post, epoch));
            var deleteBtn = UiHelper.button("Eliminar", "danger-btn");
            deleteBtn.setOnAction(e -> {
                if (UiHelper.confirm("Eliminar publicación", "¿Estás seguro de que deseas eliminar esta publicación?")) {
                    execute(() -> { api.deletePost(community.id(), post.id()); return true; },
                            ok -> switchTab("POSTS", epoch), err -> {});
                }
            });
            actions.getChildren().addAll(editBtn, deleteBtn);
        } else if ("ACTIVE".equals(post.status())) {
            var reportBtn = UiHelper.button("Reportar", "secondary");
            reportBtn.setOnAction(e -> reportDialog("POST", post.id()));
            actions.getChildren().add(reportBtn);
        }

        card.getChildren().add(actions);

        // Sección de comentarios
        var commentsContainer = new VBox(8);
        commentsContainer.setPadding(new Insets(8, 0, 0, 0));
        card.getChildren().add(commentsContainer);

        loadComments(post, commentsContainer, epoch);

        return card;
    }

    private void loadComments(ApiClient.Post post, VBox container, long epoch) {
        execute(() -> api.comments(community.id(), post.id()), comments -> {
            if (!scope.current(epoch)) return;
            container.getChildren().clear();

            if (!comments.isEmpty()) {
                container.getChildren().add(UiHelper.label("Respuestas (" + comments.size() + ")", "field-label"));
            }

            for (var c : comments) {
                var commentCard = new VBox(4);
                commentCard.getStyleClass().add(c.isSolution() ? "solution-card" : "comment-card");

                var authorRow = new HBox(8,
                        UiHelper.label(c.authorAlias(), "field-label"),
                        c.isSolution() ? UiHelper.badge("✓ SOLUCIÓN", "badge-resolved") : new Label(),
                        UiHelper.hSpacer(),
                        UiHelper.label(UiHelper.formatDate(c.createdAt()), "muted")
                );
                authorRow.setAlignment(Pos.CENTER_LEFT);

                commentCard.getChildren().addAll(
                        authorRow,
                        UiHelper.label(c.body() == null ? "(Comentario eliminado u oculto)" : c.body(), "post-body")
                );

                if ("ACTIVE".equals(c.status()) && c.authorAccountId().equals(api.accountId())) {
                    var deleteCommentBtn = UiHelper.button("Eliminar", "danger-btn");
                    deleteCommentBtn.setOnAction(e -> {
                        if (UiHelper.confirm("Eliminar comentario", "¿Deseas eliminar este comentario?")) {
                            execute(() -> { api.deleteComment(community.id(), c.id()); return true; },
                                    ok -> loadComments(post, container, epoch), err -> {});
                        }
                    });
                    commentCard.getChildren().add(deleteCommentBtn);
                }

                container.getChildren().add(commentCard);
            }

            // Input para nuevo comentario
            if ("ACTIVE".equals(post.status())) {
                var commentInput = new TextField();
                commentInput.setPromptText("Escribe un comentario o respuesta...");
                var submitComment = UiHelper.button("Responder", "primary");
                submitComment.setOnAction(e -> {
                    String text = commentInput.getText().strip();
                    if (text.isEmpty() || text.length() > 5000) return;
                    submitComment.setDisable(true);
                    execute(() -> api.comment(community.id(), post.id(), text), res -> {
                        commentInput.clear();
                        submitComment.setDisable(false);
                        loadComments(post, container, epoch);
                    }, err -> submitComment.setDisable(false));
                });
                var inputRow = new HBox(8, commentInput, submitComment);
                HBox.setHgrow(commentInput, Priority.ALWAYS);
                container.getChildren().add(inputRow);
            }
        }, err -> {});
    }

    private void editPostDialog(ApiClient.Post post, long epoch) {
        var dialog = new Dialog<Boolean>();
        dialog.setTitle("Editar publicación");
        dialog.setHeaderText("Modifica el título o cuerpo");

        var saveBtn = new ButtonType("Guardar cambios", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        var titleF = new TextField(post.title());
        var bodyA = new TextArea(post.body());
        bodyA.setWrapText(true);

        dialog.getDialogPane().setContent(new VBox(10,
                UiHelper.label("Título:", "field-label"), titleF,
                UiHelper.label("Contenido:", "field-label"), bodyA
        ));

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                execute(() -> {
                    api.editPost(community.id(), post.id(), titleF.getText().strip(), bodyA.getText().strip());
                    return true;
                }, ok -> switchTab("POSTS", epoch), err -> {});
                return true;
            }
            return false;
        });
        dialog.showAndWait();
    }

    private void reportDialog(String targetType, String targetId) {
        var dialog = new Dialog<String>();
        dialog.setTitle("Reportar contenido a moderación");
        dialog.setHeaderText("Explica el motivo del reporte (3–1000 caracteres):");

        var submit = new ButtonType("Enviar reporte", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submit, ButtonType.CANCEL);

        var reasonArea = new TextArea();
        reasonArea.setPromptText("Describe brevemente el incumplimiento de normas...");
        reasonArea.setWrapText(true);
        dialog.getDialogPane().setContent(reasonArea);

        dialog.setResultConverter(btn -> btn == submit ? reasonArea.getText().strip() : null);
        dialog.showAndWait().ifPresent(reason -> {
            if (reason.length() >= 3) {
                execute(() -> {
                    api.report(community.id(), targetType, targetId, reason);
                    return true;
                }, ok -> UiHelper.alert("Reporte recibido", "El reporte ha sido enviado al equipo de moderación.", Alert.AlertType.INFORMATION),
                err -> UiHelper.alert("Aviso", "Ya existe un reporte abierto para este contenido o no se pudo registrar.", Alert.AlertType.WARNING));
            }
        });
    }

    // =========================================================================
    // Pestaña 2: Q&A Académico (Dudas y Soluciones)
    // =========================================================================

    private void renderQaTab(long epoch) {
        var askQuestionBtn = UiHelper.button("+ Hacer una Pregunta Académica", "primary");
        var searchField = new TextField();
        searchField.setPromptText("Buscar por palabras clave o título…");

        var filterAll = UiHelper.button("Todas", "secondary");
        var filterResolved = UiHelper.button("✓ Resueltas", "secondary");
        var filterPending = UiHelper.button("○ Pendientes", "secondary");

        var filtersBox = new HBox(8, filterAll, filterResolved, filterPending, UiHelper.hSpacer(), askQuestionBtn);
        filtersBox.setAlignment(Pos.CENTER_LEFT);

        var questionsList = new VBox(12);
        var status = UiHelper.label("Cargando preguntas de la comunidad…", "muted");

        askQuestionBtn.setOnAction(e -> showNewQuestionDialog(epoch));

        Consumer<String> loadQuestions = (filterState) -> {
            status.setText("Consultando preguntas…");
            execute(() -> api.questions(community.id(), filterState, null, searchField.getText().strip()), qList -> {
                if (!scope.current(epoch)) return;
                questionsList.getChildren().clear();
                if (qList.isEmpty()) {
                    status.setText("No se encontraron preguntas para este filtro.");
                } else {
                    status.setText("Mostrando " + qList.size() + " preguntas académicas.");
                }

                for (var q : qList) {
                    questionsList.getChildren().add(buildQuestionCard(q, epoch));
                }
            }, err -> {
                if (!scope.current(epoch)) return;
                status.setText("Aún no hay preguntas registradas o la función está en preparación.");
            });
        };

        filterAll.setOnAction(e -> loadQuestions.accept("ALL"));
        filterResolved.setOnAction(e -> loadQuestions.accept("RESOLVED"));
        filterPending.setOnAction(e -> loadQuestions.accept("PENDING"));
        searchField.setOnAction(e -> loadQuestions.accept("ALL"));

        mainContainer.getChildren().setAll(
                UiHelper.label("Preguntas y Respuestas Académicas", "section-title"),
                UiHelper.label("Comparte dudas de clases y materias. El autor puede marcar la respuesta más útil como solución.", "muted"),
                searchField,
                filtersBox,
                status,
                questionsList
        );

        loadQuestions.accept("ALL");
    }

    private VBox buildQuestionCard(ApiClient.Question q, long epoch) {
        var card = UiHelper.card();

        var badge = q.isResolved()
                ? UiHelper.badge("✓ RESUELTA", "badge-resolved")
                : UiHelper.badge("○ PENDIENTE", "badge-pending");

        var subjectBadge = q.subjectTag() != null && !q.subjectTag().isBlank()
                ? UiHelper.badge("Materia: " + q.subjectTag(), "tag-pill") : new Label();

        var header = new HBox(10, badge, subjectBadge, UiHelper.label(q.title(), "card-title"), UiHelper.hSpacer(),
                UiHelper.label(q.authorAlias() + " · " + UiHelper.formatDate(q.createdAt()), "muted"));
        header.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(header, UiHelper.label(q.body(), "post-body"));

        // Respuestas a la pregunta
        var answersContainer = new VBox(8);
        answersContainer.setPadding(new Insets(8, 0, 0, 0));
        card.getChildren().add(answersContainer);

        loadQaAnswers(q, answersContainer, epoch);

        return card;
    }

    private void loadQaAnswers(ApiClient.Question q, VBox container, long epoch) {
        execute(() -> api.comments(community.id(), q.id()), comments -> {
            if (!scope.current(epoch)) return;
            container.getChildren().clear();

            boolean isAuthor = q.authorAccountId().equals(api.accountId());

            if (!comments.isEmpty()) {
                container.getChildren().add(UiHelper.label("Respuestas (" + comments.size() + "):", "field-label"));
            }

            for (var c : comments) {
                var answerCard = new VBox(6);
                answerCard.getStyleClass().add(c.isSolution() ? "solution-card" : "comment-card");

                var topRow = new HBox(8,
                        UiHelper.label(c.authorAlias(), "field-label"),
                        c.isSolution() ? UiHelper.badge("★ SOLUCIÓN ACEPTADA", "badge-resolved") : new Label(),
                        UiHelper.hSpacer(),
                        UiHelper.label(UiHelper.formatDate(c.createdAt()), "muted")
                );
                topRow.setAlignment(Pos.CENTER_LEFT);

                answerCard.getChildren().addAll(topRow, UiHelper.label(c.body(), "post-body"));

                // Si soy el autor de la duda y esta respuesta no es la solución, puedo marcarla
                if (isAuthor && !c.isSolution() && "ACTIVE".equals(c.status())) {
                    var acceptBtn = UiHelper.button("✓ Marcar como solución", "success-btn");
                    acceptBtn.setOnAction(e -> {
                        execute(() -> {
                            api.acceptSolution(community.id(), q.id(), c.id());
                            return true;
                        }, ok -> switchTab("QA", epoch), err -> {});
                    });
                    answerCard.getChildren().add(acceptBtn);
                }

                container.getChildren().add(answerCard);
            }

            // Input para responder
            var answerInput = new TextField();
            answerInput.setPromptText("Escribe una respuesta académica o procedimiento…");
            var submitAnswer = UiHelper.button("Aportar respuesta", "primary");
            submitAnswer.setOnAction(e -> {
                String text = answerInput.getText().strip();
                if (text.isEmpty()) return;
                submitAnswer.setDisable(true);
                execute(() -> api.comment(community.id(), q.id(), text), res -> {
                    answerInput.clear();
                    submitAnswer.setDisable(false);
                    loadQaAnswers(q, container, epoch);
                }, err -> submitAnswer.setDisable(false));
            });

            var inputRow = new HBox(8, answerInput, submitAnswer);
            HBox.setHgrow(answerInput, Priority.ALWAYS);
            container.getChildren().add(inputRow);
        }, err -> {});
    }

    private void showNewQuestionDialog(long epoch) {
        var dialog = new Dialog<Boolean>();
        dialog.setTitle("Publicar Pregunta Académica");
        dialog.setHeaderText("Formula tu duda con claridad para recibir explicaciones de tus compañeros");

        var publishBtn = new ButtonType("Publicar pregunta", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(publishBtn, ButtonType.CANCEL);

        var titleF = new TextField();
        titleF.setPromptText("Ej: ¿Cómo implementar el algoritmo de Dijkstra en Java?");
        var subjF = new TextField();
        subjF.setPromptText("Ej: Estructuras de Datos / Cálculo / Física");
        var tagsF = new TextField();
        tagsF.setPromptText("Etiquetas separadas por comas (ej. java, grafos, complejidad)");
        var bodyA = new TextArea();
        bodyA.setPromptText("Explica el contexto del problema, lo que has intentado y el error o duda puntual…");
        bodyA.setWrapText(true);
        bodyA.setPrefRowCount(6);

        dialog.getDialogPane().setContent(new VBox(10,
                UiHelper.label("Título de la pregunta (3–160 caracteres):", "field-label"), titleF,
                UiHelper.label("Materia o Temática:", "field-label"), subjF,
                UiHelper.label("Etiquetas:", "field-label"), tagsF,
                UiHelper.label("Detalle del problema y código:", "field-label"), bodyA
        ));

        dialog.setResultConverter(btn -> {
            if (btn == publishBtn) {
                String title = titleF.getText().strip();
                String body = bodyA.getText().strip();
                if (title.length() < 3 || body.isEmpty()) {
                    UiHelper.alert("Error", "Completa el título y detalle de la pregunta.", Alert.AlertType.WARNING);
                    return false;
                }
                List<String> tags = new ArrayList<>();
                for (String t : tagsF.getText().split(",")) {
                    if (!t.isBlank()) tags.add(t.strip());
                }

                execute(() -> api.createQuestion(community.id(), UUID.randomUUID(), title, body, subjF.getText().strip(), tags),
                        q -> switchTab("QA", epoch),
                        err -> UiHelper.alert("Error", "No se pudo registrar la pregunta.", Alert.AlertType.ERROR));
                return true;
            }
            return false;
        });

        dialog.showAndWait();
    }

    // =========================================================================
    // Pestaña 3: Biblioteca de Recursos Académicos
    // =========================================================================

    private void renderResourcesTab(long epoch) {
        var uploadBtn = UiHelper.button("+ Compartir Recurso / Material", "primary");
        var searchField = new TextField();
        searchField.setPromptText("Buscar por título de apunte, guía o tema…");

        var filterAll = UiHelper.button("Todos", "secondary");
        var filterDocs = UiHelper.button("Documentos/PDF", "secondary");
        var filterSlides = UiHelper.button("Presentaciones", "secondary");
        var filterLinks = UiHelper.button("Enlaces", "secondary");

        var filtersBox = new HBox(8, filterAll, filterDocs, filterSlides, filterLinks, UiHelper.hSpacer(), uploadBtn);
        filtersBox.setAlignment(Pos.CENTER_LEFT);

        var resourcesList = new VBox(12);
        var status = UiHelper.label("Cargando recursos académicos compartidos…", "muted");

        Consumer<String> loadRes = (type) -> {
            status.setText("Consultando recursos…");
            execute(() -> api.resources(community.id(), type, null, searchField.getText().strip()), list -> {
                if (!scope.current(epoch)) return;
                resourcesList.getChildren().clear();
                if (list.isEmpty()) {
                    status.setText("Aún no se han compartido recursos en esta categoría.");
                } else {
                    status.setText("Se encontraron " + list.size() + " recursos disponibles.");
                }

                for (var r : list) {
                    var card = UiHelper.card(
                            new HBox(10,
                                    UiHelper.badge(r.resourceType(), "badge-role"),
                                    UiHelper.label(r.title(), "card-title"),
                                    UiHelper.hSpacer(),
                                    UiHelper.label("Subido por " + r.uploaderAlias() + " · " + UiHelper.formatDate(r.createdAt()), "muted")
                            ),
                            UiHelper.label(r.description() == null ? "" : r.description(), "muted")
                    );

                    if (r.externalUrl() != null && !r.externalUrl().isBlank()) {
                        var linkBtn = UiHelper.button("🔗 Abrir enlace: " + r.externalUrl(), "secondary");
                        linkBtn.setOnAction(e -> {
                            var clip = Clipboard.getSystemClipboard();
                            var content = new ClipboardContent();
                            content.putString(r.externalUrl());
                            clip.setContent(content);
                            UiHelper.alert("Enlace copiado", "Se copió la URL al portapapeles: " + r.externalUrl(), Alert.AlertType.INFORMATION);
                        });
                        card.getChildren().add(linkBtn);
                    }

                    resourcesList.getChildren().add(card);
                }
            }, err -> {
                if (!scope.current(epoch)) return;
                status.setText("Espacio de recursos preparado. Puedes compartir el primer documento o enlace.");
            });
        };

        filterAll.setOnAction(e -> loadRes.accept("ALL"));
        filterDocs.setOnAction(e -> loadRes.accept("DOCUMENT"));
        filterSlides.setOnAction(e -> loadRes.accept("SLIDES"));
        filterLinks.setOnAction(e -> loadRes.accept("EXTERNAL_LINK"));
        searchField.setOnAction(e -> loadRes.accept("ALL"));

        uploadBtn.setOnAction(e -> showUploadResourceDialog(epoch));

        mainContainer.getChildren().setAll(
                UiHelper.label("Biblioteca de Recursos de la Comunidad", "section-title"),
                UiHelper.label("Comparte y consulta guías, presentaciones, apuntes y enlaces de estudio para tu grupo.", "muted"),
                searchField,
                filtersBox,
                status,
                resourcesList
        );

        loadRes.accept("ALL");
    }

    private void showUploadResourceDialog(long epoch) {
        var dialog = new Dialog<Boolean>();
        dialog.setTitle("Compartir Recurso Académico");
        dialog.setHeaderText("Ingresa los datos del material o enlace educativo");

        var saveBtn = new ButtonType("Compartir recurso", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        var titleF = new TextField();
        titleF.setPromptText("Ej: Apuntes completos de Álgebra Lineal - Semana 1 a 5");
        var descA = new TextArea();
        descA.setPromptText("Descripción de los temas cubiertos en el material…");
        descA.setPrefRowCount(3);

        var typeCombo = new ComboBox<String>();
        typeCombo.getItems().addAll("DOCUMENT", "PDF", "SLIDES", "LAB_GUIDE", "EXTERNAL_LINK");
        typeCombo.setValue("DOCUMENT");

        var urlField = new TextField();
        urlField.setPromptText("https://... (para enlaces externos o repositorios)");
        var subjectF = new TextField();
        subjectF.setPromptText("Nombre de la asignatura (opcional)");

        dialog.getDialogPane().setContent(new VBox(10,
                UiHelper.label("Título del recurso:", "field-label"), titleF,
                UiHelper.label("Tipo de recurso:", "field-label"), typeCombo,
                UiHelper.label("Materia asociada:", "field-label"), subjectF,
                UiHelper.label("Enlace web / URL externa (opcional):", "field-label"), urlField,
                UiHelper.label("Descripción:", "field-label"), descA
        ));

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String title = titleF.getText().strip();
                if (title.length() < 3) {
                    UiHelper.alert("Aviso", "El título debe tener al menos 3 caracteres.", Alert.AlertType.WARNING);
                    return false;
                }

                execute(() -> api.publishResource(
                        community.id(),
                        title,
                        descA.getText().strip(),
                        typeCombo.getValue(),
                        urlField.getText().strip(),
                        null,
                        null,
                        null,
                        subjectF.getText().strip()
                ), res -> switchTab("RESOURCES", epoch), err -> UiHelper.alert("Error", "No se pudo publicar el recurso.", Alert.AlertType.ERROR));
                return true;
            }
            return false;
        });

        dialog.showAndWait();
    }

    // =========================================================================
    // Pestaña 4: Calendario y Actividades
    // =========================================================================

    private void renderCalendarTab(long epoch) {
        var createEventBtn = UiHelper.button("+ Planificar Actividad / Examen", "primary");
        var eventsList = new VBox(12);
        var status = UiHelper.label("Cargando actividades y fechas importantes…", "muted");

        createEventBtn.setOnAction(e -> showCreateEventDialog(epoch));

        Runnable loadEvts = () -> {
            execute(() -> api.events(community.id(), null, null), list -> {
                if (!scope.current(epoch)) return;
                eventsList.getChildren().clear();
                if (list.isEmpty()) {
                    status.setText("No hay eventos programados en esta comunidad.");
                } else {
                    status.setText("Se encontraron " + list.size() + " actividades programadas.");
                }

                for (var ev : list) {
                    var card = UiHelper.card(
                            new HBox(10,
                                    UiHelper.badge(ev.eventType(), "badge-role"),
                                    UiHelper.label(ev.title(), "card-title"),
                                    UiHelper.hSpacer(),
                                    UiHelper.label("Fecha: " + UiHelper.formatDate(ev.eventDate()), "muted")
                            ),
                            UiHelper.label(ev.description() == null ? "" : ev.description(), "muted")
                    );
                    eventsList.getChildren().add(card);
                }
            }, err -> {
                if (!scope.current(epoch)) return;
                status.setText("Módulo de calendario disponible para programar fechas de estudio.");
            });
        };

        mainContainer.getChildren().setAll(
                new HBox(12, UiHelper.label("Calendario y Organización de Actividades", "section-title"), UiHelper.hSpacer(), createEventBtn),
                UiHelper.label("Fechas límite de entregas, exámenes y sesiones grupales de la comunidad.", "muted"),
                status,
                eventsList
        );

        loadEvts.run();
    }

    private void showCreateEventDialog(long epoch) {
        var dialog = new Dialog<Boolean>();
        dialog.setTitle("Planificar Actividad Comunitaria");
        dialog.setHeaderText("Registra una fecha académica clave para la comunidad");

        var saveBtn = new ButtonType("Guardar evento", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        var titleF = new TextField();
        titleF.setPromptText("Ej: Examen Parcial de Cálculo Diferencial");
        var descA = new TextArea();
        descA.setPromptText("Temas a evaluar, aula o enlace de reunión…");
        descA.setPrefRowCount(3);

        var typeCombo = new ComboBox<String>();
        typeCombo.getItems().addAll("EXAM", "DELIVERY", "WORKSHOP", "MEETING", "REMINDER");
        typeCombo.setValue("EXAM");

        var datePicker = new DatePicker();
        datePicker.setValue(java.time.LocalDate.now().plusDays(7));

        dialog.getDialogPane().setContent(new VBox(10,
                UiHelper.label("Título de la actividad:", "field-label"), titleF,
                UiHelper.label("Tipo de actividad:", "field-label"), typeCombo,
                UiHelper.label("Fecha:", "field-label"), datePicker,
                UiHelper.label("Descripción:", "field-label"), descA
        ));

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String title = titleF.getText().strip();
                if (title.length() < 3) {
                    UiHelper.alert("Aviso", "El título debe tener al menos 3 caracteres.", Alert.AlertType.WARNING);
                    return false;
                }
                var ldt = datePicker.getValue().atTime(12, 0);
                var instant = ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();

                execute(() -> api.createEvent(community.id(), title, descA.getText().strip(), typeCombo.getValue(), instant.toString()),
                        ev -> switchTab("CALENDAR", epoch),
                        err -> UiHelper.alert("Error", "No se pudo registrar el evento.", Alert.AlertType.ERROR));
                return true;
            }
            return false;
        });

        dialog.showAndWait();
    }

    // =========================================================================
    // Pestaña 5: Integrantes, Invitaciones y Administración
    // =========================================================================

    private void renderMembersTab(long epoch) {
        var inviteBtn = UiHelper.button("🔑 Generar Código de Invitación", "primary");
        inviteBtn.setOnAction(e -> showGenerateInviteDialog(epoch));

        var membersList = new VBox(10);
        var status = UiHelper.label("Cargando lista de participantes…", "muted");

        boolean isAdmin = "ADMIN".equals(community.role());

        Runnable loadMbrs = () -> {
            execute(() -> api.members(community.id()), list -> {
                if (!scope.current(epoch)) return;
                membersList.getChildren().clear();
                status.setText("Total de miembros activos: " + list.size());

                for (var m : list) {
                    var roleBadge = UiHelper.badge(m.role(),
                            "ADMIN".equals(m.role()) ? "badge-admin" :
                            "MODERATOR".equals(m.role()) ? "badge-mod" : "badge-role");

                    var row = new HBox(12,
                            UiHelper.label(m.alias(), "field-label"),
                            roleBadge,
                            UiHelper.label(m.declaredUniversity() != null ? m.declaredUniversity() : "", "muted"),
                            UiHelper.hSpacer()
                    );
                    row.setAlignment(Pos.CENTER_LEFT);

                    // Si soy ADMIN de la comunidad, puedo promover a MODERATOR o retirar miembro
                    if (isAdmin && !m.accountId().equals(api.accountId())) {
                        if ("MEMBER".equals(m.role())) {
                            var promoteBtn = UiHelper.button("Designar Moderador", "secondary");
                            promoteBtn.setOnAction(e -> {
                                execute(() -> {
                                    api.updateMemberRole(community.id(), m.accountId(), "MODERATOR");
                                    return true;
                                }, ok -> switchTab("MEMBERS", epoch), err -> {});
                            });
                            row.getChildren().add(promoteBtn);
                        } else if ("MODERATOR".equals(m.role())) {
                            var demoteBtn = UiHelper.button("Quitar Moderador", "secondary");
                            demoteBtn.setOnAction(e -> {
                                execute(() -> {
                                    api.updateMemberRole(community.id(), m.accountId(), "MEMBER");
                                    return true;
                                }, ok -> switchTab("MEMBERS", epoch), err -> {});
                            });
                            row.getChildren().add(demoteBtn);
                        }

                        var removeBtn = UiHelper.button("Retirar", "danger-btn");
                        removeBtn.setOnAction(e -> {
                            if (UiHelper.confirm("Retirar miembro", "¿Deseas retirar a " + m.alias() + " de la comunidad?")) {
                                execute(() -> {
                                    api.removeMember(community.id(), m.accountId());
                                    return true;
                                }, ok -> switchTab("MEMBERS", epoch), err -> {});
                            }
                        });
                        row.getChildren().add(removeBtn);
                    }

                    var card = UiHelper.card(row);
                    membersList.getChildren().add(card);
                }
            }, err -> {
                if (!scope.current(epoch)) return;
                status.setText("Comunidad con administración gráfica disponible.");
            });
        };

        var headerRow = new HBox(12,
                UiHelper.label("Gestión de Integrantes y Roles", "section-title"),
                UiHelper.hSpacer(),
                inviteBtn
        );
        headerRow.setAlignment(Pos.CENTER_LEFT);

        mainContainer.getChildren().setAll(
                headerRow,
                UiHelper.label("Administra los permisos de moderación y genera invitaciones seguras sin usar scripts.", "muted"),
                status,
                membersList
        );

        loadMbrs.run();
    }

    private void showGenerateInviteDialog(long epoch) {
        // Generar código aleatorio amigable de 8 caracteres
        String defaultCode = "CAMPUS" + (1000 + new Random().nextInt(9000));

        var dialog = new Dialog<Boolean>();
        dialog.setTitle("Crear Código de Invitación");
        dialog.setHeaderText("Genera un código seguro para que otros estudiantes se unan a esta comunidad");

        var createBtn = new ButtonType("Generar código", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        var codeField = new TextField(defaultCode);
        var usesField = new TextField("50");
        usesField.setPromptText("Límite de usos (número)");

        dialog.getDialogPane().setContent(new VBox(10,
                UiHelper.label("Código alfanumérico (6 a 16 caracteres):", "field-label"), codeField,
                UiHelper.label("Límite máximo de usos:", "field-label"), usesField
        ));

        dialog.setResultConverter(btn -> {
            if (btn == createBtn) {
                String code = codeField.getText().strip().toUpperCase(Locale.ROOT);
                Integer maxUses = null;
                try {
                    maxUses = Integer.parseInt(usesField.getText().strip());
                } catch (Exception ignored) {}

                Integer finalUses = maxUses;
                execute(() -> api.createInvitation(community.id(), code, finalUses, null), inv -> {
                    var clip = Clipboard.getSystemClipboard();
                    var content = new ClipboardContent();
                    content.putString(code);
                    clip.setContent(content);

                    UiHelper.alert("Invitación generada",
                            "Código: " + code + "\n\nSe ha copiado automáticamente al portapapeles. Compártelo con los estudiantes para que se unan.",
                            Alert.AlertType.INFORMATION);
                }, err -> UiHelper.alert("Error", "No se pudo generar la invitación. Revisa que el código no esté repetido.", Alert.AlertType.ERROR));
                return true;
            }
            return false;
        });

        dialog.showAndWait();
    }

    private <T> void execute(java.util.concurrent.Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        long expected = scope.generation();
        workers.submit(() -> {
            try {
                T result = task.call();
                Platform.runLater(() -> {
                    if (scope.current(expected)) onSuccess.accept(result);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    if (scope.current(expected)) onError.accept(t);
                });
            }
        });
    }
}
