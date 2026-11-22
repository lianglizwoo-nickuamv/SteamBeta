package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Detalle de Comunidad (FXML) — SteamBeta Campus:
 * Gestiona pestañas de Conversaciones (Posts), Dudas y Soluciones (Q&A),
 * Recursos, Calendario de la comunidad y Administración de Integrantes.
 * 100% basado en FXML.
 */
public final class CommunityDetailController {

    public interface CommunityDetailNavigationListener {
        void onBackToHome();
        void onOpenModeration(ApiClient.Community community);
    }

    @FXML private Label communityNameLabel;
    @FXML private Label roleBadge;
    @FXML private Label descLabel;
    @FXML private Label rulesLabel;

    @FXML private HBox tabHeader;
    @FXML private Button btnPosts;
    @FXML private Button btnQA;
    @FXML private Button btnResources;
    @FXML private Button btnCalendar;
    @FXML private Button btnMembers;
    @FXML private Button btnModeration;

    @FXML private Label tabStatusLabel;
    @FXML private VBox tabContentPane;
    @FXML private VBox bottomComposer;
    @FXML private TextField composerTitleField;
    @FXML private TextArea composerBodyArea;
    @FXML private Label composerStatus;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private ApiClient.Community community;
    private CommunityDetailNavigationListener navigation;
    private String activeTab = "POSTS";

    public void init(ApiClient.Community community, ApiClient api, SessionScope scope,
                     ExecutorService workers, CommunityDetailNavigationListener navigation) {
        this.community = community;
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.navigation = navigation;

        communityNameLabel.setText(community.name());
        descLabel.setText(community.description() != null ? community.description() : "");
        rulesLabel.setText("Reglas: " + (community.rules() != null ? community.rules() : "Respeto mutuo y rigor académico."));

        String role = community.role() != null ? community.role() : "MEMBER";
        roleBadge.setText("Rol: " + role);
        roleBadge.getStyleClass().removeAll("badge-admin", "badge-mod", "badge-role");
        if ("ADMIN".equals(role)) {
            roleBadge.getStyleClass().add("badge-admin");
        } else if ("MODERATOR".equals(role)) {
            roleBadge.getStyleClass().add("badge-mod");
        } else {
            roleBadge.getStyleClass().add("badge-role");
        }

        if ("ADMIN".equals(role) || "MODERATOR".equals(role)) {
            btnModeration.setVisible(true);
            btnModeration.setManaged(true);
        }

        switchTab("POSTS");
    }

    private void switchTab(String tab) {
        this.activeTab = tab;
        tabContentPane.getChildren().clear();

        // Actualizar estilos activos de pestañas
        btnPosts.getStyleClass().removeAll("tab-btn-active", "tab-btn");
        btnQA.getStyleClass().removeAll("tab-btn-active", "tab-btn");
        btnResources.getStyleClass().removeAll("tab-btn-active", "tab-btn");
        btnCalendar.getStyleClass().removeAll("tab-btn-active", "tab-btn");
        btnMembers.getStyleClass().removeAll("tab-btn-active", "tab-btn");

        btnPosts.getStyleClass().add("POSTS".equals(tab) ? "tab-btn-active" : "tab-btn");
        btnQA.getStyleClass().add("QA".equals(tab) ? "tab-btn-active" : "tab-btn");
        btnResources.getStyleClass().add("RESOURCES".equals(tab) ? "tab-btn-active" : "tab-btn");
        btnCalendar.getStyleClass().add("CALENDAR".equals(tab) ? "tab-btn-active" : "tab-btn");
        btnMembers.getStyleClass().add("MEMBERS".equals(tab) ? "tab-btn-active" : "tab-btn");

        boolean showComposer = "POSTS".equals(tab);
        bottomComposer.setVisible(showComposer);
        bottomComposer.setManaged(showComposer);

        switch (tab) {
            case "POSTS" -> loadPostsTab();
            case "QA" -> loadQaTab();
            case "RESOURCES" -> loadResourcesTab();
            case "CALENDAR" -> loadCalendarTab();
            case "MEMBERS" -> loadMembersTab();
        }
    }

    // =========================================================================
    // 1. PESTAÑA: CONVERSACIONES Y POSTS
    // =========================================================================

    private void loadPostsTab() {
        long epoch = scope.generation();
        tabStatusLabel.setVisible(true);
        tabStatusLabel.setManaged(true);
        tabStatusLabel.setText("Cargando publicaciones de la comunidad…");

        workers.submit(() -> {
            try {
                var page = api.posts(community.id(), null, null);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    tabContentPane.getChildren().clear();
                    tabStatusLabel.setVisible(false);
                    tabStatusLabel.setManaged(false);

                    if (page.posts().isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("💬", "Aún no hay publicaciones",
                                "Sé el primero en compartir un tema o pregunta utilizando el editor inferior.", null, null);
                        tabContentPane.getChildren().add(empty.root());
                    } else {
                        for (var post : page.posts()) {
                            var postView = ViewLoader.<PostCardController>load("PostCard.fxml");
                            postView.controller().init(post, community.id(), api, scope, workers, () -> switchTab("POSTS"));
                            tabContentPane.getChildren().add(postView.root());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    tabStatusLabel.setVisible(true);
                    tabStatusLabel.setManaged(true);
                    tabStatusLabel.setText("No se pudieron cargar las publicaciones.");
                });
            }
        });
    }

    // =========================================================================
    // 2. PESTAÑA: PREGUNTAS Y SOLUCIONES (Q&A)
    // =========================================================================

    private void loadQaTab() {
        long epoch = scope.generation();
        tabStatusLabel.setVisible(true);
        tabStatusLabel.setManaged(true);
        tabStatusLabel.setText("Consultando preguntas académicas…");

        workers.submit(() -> {
            try {
                var questions = api.questions(community.id(), null, null, null);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    tabContentPane.getChildren().clear();
                    tabStatusLabel.setVisible(false);
                    tabStatusLabel.setManaged(false);

                    if (questions.isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("❓", "No hay preguntas en esta comunidad",
                                "Puedes formular consultas conceptuales o ejercicios para recibir apoyo.", null, null);
                        tabContentPane.getChildren().add(empty.root());
                    } else {
                        for (var q : questions) {
                            var qCard = ViewLoader.<QuestionCardController>load("QuestionCard.fxml");
                            qCard.controller().set(q, null);
                            tabContentPane.getChildren().add(qCard.root());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    tabStatusLabel.setVisible(true);
                    tabStatusLabel.setManaged(true);
                    tabStatusLabel.setText("Error al cargar preguntas Q&A.");
                });
            }
        });
    }

    // =========================================================================
    // 3. PESTAÑA: RECURSOS ACADÉMICOS
    // =========================================================================

    private void loadResourcesTab() {
        long epoch = scope.generation();
        tabStatusLabel.setVisible(true);
        tabStatusLabel.setManaged(true);
        tabStatusLabel.setText("Consultando biblioteca de recursos…");

        workers.submit(() -> {
            try {
                var items = api.resources(community.id(), null, null, null);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    tabContentPane.getChildren().clear();
                    tabStatusLabel.setVisible(false);
                    tabStatusLabel.setManaged(false);

                    if (items.isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("📚", "Sin archivos o enlaces de recursos",
                                "Aquí se comparten guías, libros recomendados y material de apoyo.", null, null);
                        tabContentPane.getChildren().add(empty.root());
                    } else {
                        for (var r : items) {
                            var rCard = ViewLoader.<ResourceCardController>load("ResourceCard.fxml");
                            rCard.controller().set(r);
                            tabContentPane.getChildren().add(rCard.root());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    tabStatusLabel.setVisible(true);
                    tabStatusLabel.setManaged(true);
                    tabStatusLabel.setText("Error al cargar recursos.");
                });
            }
        });
    }

    // =========================================================================
    // 4. PESTAÑA: CALENDARIO DE LA COMUNIDAD
    // =========================================================================

    private void loadCalendarTab() {
        long epoch = scope.generation();
        tabStatusLabel.setVisible(true);
        tabStatusLabel.setManaged(true);
        tabStatusLabel.setText("Consultando agenda comunitaria…");

        workers.submit(() -> {
            try {
                var events = api.events(community.id(), null, null);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    tabContentPane.getChildren().clear();
                    tabStatusLabel.setVisible(false);
                    tabStatusLabel.setManaged(false);

                    if (events.isEmpty()) {
                        var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                        empty.controller().set("📅", "No hay eventos programados",
                                "No se registran exámenes ni entregas próximas en esta comunidad.", null, null);
                        tabContentPane.getChildren().add(empty.root());
                    } else {
                        for (var ev : events) {
                            var evCard = ViewLoader.<EventCardController>load("EventCard.fxml");
                            evCard.controller().set(ev);
                            tabContentPane.getChildren().add(evCard.root());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    tabStatusLabel.setVisible(true);
                    tabStatusLabel.setManaged(true);
                    tabStatusLabel.setText("Error al cargar eventos.");
                });
            }
        });
    }

    // =========================================================================
    // 5. PESTAÑA: INTEGRANTES Y GESTIÓN
    // =========================================================================

    private void loadMembersTab() {
        long epoch = scope.generation();
        tabStatusLabel.setVisible(true);
        tabStatusLabel.setManaged(true);
        tabStatusLabel.setText("Consultando lista de integrantes…");

        workers.submit(() -> {
            try {
                var members = api.members(community.id());
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    tabContentPane.getChildren().clear();
                    tabStatusLabel.setVisible(false);
                    tabStatusLabel.setManaged(false);

                    boolean isAdmin = "ADMIN".equals(community.role());
                    var headerView = ViewLoader.<CommunityMembersHeaderController>load("CommunityMembersHeader.fxml");
                    headerView.controller().set(members.size(), isAdmin, this::generateInviteCode);
                    tabContentPane.getChildren().add(headerView.root());

                    for (var m : members) {
                        var memberCard = ViewLoader.<MemberCardController>load("MemberCard.fxml");
                        memberCard.controller().set(m);
                        tabContentPane.getChildren().add(memberCard.root());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    tabStatusLabel.setVisible(true);
                    tabStatusLabel.setManaged(true);
                    tabStatusLabel.setText("Error al consultar integrantes.");
                });
            }
        });
    }

    private void generateInviteCode() {
        workers.submit(() -> {
            try {
                var inv = api.createInvitation(community.id(), null, 50, null);
                Platform.runLater(() -> {
                    Stage owner = tabContentPane.getScene() != null ? (Stage) tabContentPane.getScene().getWindow() : null;
                    var successLoader = ViewLoader.<CommunityCreatedSuccessDialogController>load("CommunityCreatedSuccessDialog.fxml");
                    successLoader.controller().setDirect(community.name(), inv.code());
                    var successModal = UiHelper.createModalStage(successLoader.root(), "Código de Invitación Generado", owner);
                    successModal.showAndWait();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    Stage owner = tabContentPane.getScene() != null ? (Stage) tabContentPane.getScene().getWindow() : null;
                    UiHelper.showError(owner, "Error al generar invitación", "No se pudo generar el código: " + ex.getMessage());
                });
            }
        });
    }

    @FXML
    private void onPublishPost() {
        String title = composerTitleField.getText().strip();
        String body = composerBodyArea.getText().strip();
        if (title.length() < 3 || title.length() > 160 || body.isEmpty() || body.length() > 10000) {
            composerStatus.setText("El título debe tener 3–160 caracteres y el cuerpo hasta 10 000.");
            return;
        }

        composerTitleField.setDisable(true);
        composerBodyArea.setDisable(true);
        composerStatus.setText("Publicando…");

        workers.submit(() -> {
            try {
                api.createPost(community.id(), UUID.randomUUID(), title, body);
                Platform.runLater(() -> {
                    composerTitleField.clear();
                    composerBodyArea.clear();
                    composerTitleField.setDisable(false);
                    composerBodyArea.setDisable(false);
                    composerStatus.setText("");
                    switchTab("POSTS");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    composerTitleField.setDisable(false);
                    composerBodyArea.setDisable(false);
                    composerStatus.setText("Error al publicar en el servidor.");
                });
            }
        });
    }

    @FXML private void onBackToHome() { if (navigation != null) navigation.onBackToHome(); }
    @FXML private void onTabPosts() { switchTab("POSTS"); }
    @FXML private void onTabQA() { switchTab("QA"); }
    @FXML private void onTabResources() { switchTab("RESOURCES"); }
    @FXML private void onTabCalendar() { switchTab("CALENDAR"); }
    @FXML private void onTabMembers() { switchTab("MEMBERS"); }
    @FXML private void onOpenModeration() { if (navigation != null) navigation.onOpenModeration(community); }
}
