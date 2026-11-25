package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Detalle de Comunidad (FXML) — SteamBeta Campus:
 * Gestiona Presentación, Conversación unificada (chat comunitario + preguntas académicas),
 * Recursos, Calendario, Integrantes y salida propia.
 */
public final class CommunityDetailController {

    private record TimelineEntry(Instant timestamp, ApiClient.Post post, ApiClient.Question question) {
        boolean isQuestion() { return question != null; }
    }

    public interface CommunityDetailNavigationListener {
        void onBackToHome();
        void onOpenModeration(ApiClient.Community community);
    }

    @FXML private ScrollPane mainScrollPane;
    @FXML private Label communityNameLabel;
    @FXML private Label categoryBadge;
    @FXML private Label visibilityBadge;
    @FXML private Label roleBadge;
    @FXML private Label descLabel;
    @FXML private Label rulesLabel;
    @FXML private Button btnLeaveCommunity;

    @FXML private HBox tabHeader;
    @FXML private Button btnOverview;
    @FXML private Button btnPosts;
    @FXML private Button btnResources;
    @FXML private Button btnCalendar;
    @FXML private Button btnMembers;
    @FXML private Button btnModeration;
    @FXML private Button btnNewQuestion;
    @FXML private Button btnNewResource;
    @FXML private Button btnNewCalendarEvent;

    @FXML private HBox chatFilterBar;
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterMessages;
    @FXML private Button btnFilterQuestions;
    @FXML private Button btnFilterUnresolved;

    @FXML private Label tabPurposeLabel;
    @FXML private Label tabStatusLabel;

    // Sección Presentación (100% FXML)
    @FXML private VBox overviewPane;
    @FXML private Label overviewDescLabel;
    @FXML private Label overviewCategoryLabel;
    @FXML private Label overviewAccessLabel;
    @FXML private Label overviewRoleLabel;
    @FXML private Label overviewRulesLabel;
    @FXML private Label overviewMembersCountLabel;
    @FXML private ProgressBar overviewCapacityBar;
    @FXML private Label overviewSlotsLabel;
    @FXML private Label overviewStatsStatusLabel;
    @FXML private GridPane overviewStatsGrid;
    @FXML private Label statPostsLabel;
    @FXML private Label statQuestionsLabel;
    @FXML private Label statResolvedLabel;
    @FXML private Label statResourcesLabel;
    @FXML private Label statEventsLabel;

    @FXML private VBox tabContentPane;
    @FXML private VBox bottomComposer;
    @FXML private Button btnModeMessage;
    @FXML private Button btnModeQuestion;
    @FXML private Label composerModeHint;
    @FXML private HBox questionMetaRow;
    @FXML private TextField composerTitleField;
    @FXML private TextField composerSubjectField;
    @FXML private TextArea composerBodyArea;
    @FXML private Button btnSendComposer;
    @FXML private Label composerStatus;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private ApiClient.Community community;
    private CommunityDetailNavigationListener navigation;

    private List<TimelineEntry> cachedTimeline = List.of();
    private Map<String, String> memberAvatarUrls = Map.of();
    private String chatFilter = "ALL";
    private boolean questionComposerMode = false;

    public void init(ApiClient.Community community, ApiClient api, SessionScope scope,
                     ExecutorService workers, CommunityDetailNavigationListener navigation) {
        this.community = community;
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.navigation = navigation;

        String name = community.name() != null ? community.name() : "Comunidad";
        String desc = community.description() != null && !community.description().isBlank()
                ? community.description() : "Sin descripción registrada.";
        String rules = community.rules() != null && !community.rules().isBlank()
                ? community.rules() : "Respeto mutuo y rigor académico.";
        String role = community.role() != null && !community.role().isBlank() ? community.role() : "MEMBER";
        String categoryLabel = formatCategory(community.category());
        String accessLabel = formatVisibility(community.visibility());

        communityNameLabel.setText(name);
        descLabel.setText(desc);
        rulesLabel.setText("Reglas: " + rules);

        if (categoryBadge != null) {
            categoryBadge.setText(categoryLabel);
        }
        if (visibilityBadge != null) {
            visibilityBadge.setText(accessLabel);
        }

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

        // Botón "Salir de la comunidad" visible únicamente para miembros activos
        boolean isMember = community.role() != null && !community.role().isBlank();
        if (btnLeaveCommunity != null) {
            btnLeaveCommunity.setVisible(isMember);
            btnLeaveCommunity.setManaged(isMember);
        }

        // Poblar Presentación con datos reales
        populateOverviewStatic(desc, rules, categoryLabel, accessLabel, formatRole(role));

        // Abrir en Presentación para que el estudiante comprenda de qué trata el espacio
        switchTab("OVERVIEW");
    }

    private void populateOverviewStatic(String desc, String rules, String categoryLabel, String accessLabel, String roleLabel) {
        if (overviewDescLabel != null) overviewDescLabel.setText(desc);
        if (overviewCategoryLabel != null) overviewCategoryLabel.setText(categoryLabel);
        if (overviewAccessLabel != null) overviewAccessLabel.setText(accessLabel);
        if (overviewRoleLabel != null) overviewRoleLabel.setText(roleLabel);
        if (overviewRulesLabel != null) overviewRulesLabel.setText(rules);

        int max = community.maxMembers() > 0 ? community.maxMembers() : 50;
        long count = Math.max(0, community.memberCount());
        int slots = Math.max(0, community.availableSlots());
        if (overviewMembersCountLabel != null) {
            overviewMembersCountLabel.setText(count + " / " + max + " integrantes");
        }
        if (overviewCapacityBar != null) {
            overviewCapacityBar.setProgress(Math.min(1.0, (double) count / max));
        }
        if (overviewSlotsLabel != null) {
            overviewSlotsLabel.setText(slots == 0
                    ? "Comunidad llena (0 espacios disponibles)"
                    : slots + (slots == 1 ? " espacio disponible" : " espacios disponibles"));
        }
    }

    private void switchTab(String tab) {
        tabContentPane.getChildren().clear();

        List<Button> tabs = List.of(btnOverview, btnPosts, btnResources, btnCalendar, btnMembers);
        for (Button b : tabs) {
            if (b != null) b.getStyleClass().removeAll("tab-btn-active", "tab-btn");
        }

        if (btnOverview != null) btnOverview.getStyleClass().add("OVERVIEW".equals(tab) ? "tab-btn-active" : "tab-btn");
        btnPosts.getStyleClass().add("POSTS".equals(tab) ? "tab-btn-active" : "tab-btn");
        btnResources.getStyleClass().add("RESOURCES".equals(tab) ? "tab-btn-active" : "tab-btn");
        btnCalendar.getStyleClass().add("CALENDAR".equals(tab) ? "tab-btn-active" : "tab-btn");
        btnMembers.getStyleClass().add("MEMBERS".equals(tab) ? "tab-btn-active" : "tab-btn");

        boolean isOverview = "OVERVIEW".equals(tab);
        if (overviewPane != null) {
            overviewPane.setVisible(isOverview);
            overviewPane.setManaged(isOverview);
        }

        boolean isConversation = "POSTS".equals(tab);
        bottomComposer.setVisible(isConversation);
        bottomComposer.setManaged(isConversation);
        if (chatFilterBar != null) {
            chatFilterBar.setVisible(isConversation);
            chatFilterBar.setManaged(isConversation);
        }
        if (btnNewQuestion != null) {
            btnNewQuestion.setVisible(isConversation);
            btnNewQuestion.setManaged(isConversation);
        }

        boolean isCalendar = "CALENDAR".equals(tab);
        if (btnNewCalendarEvent != null) {
            btnNewCalendarEvent.setVisible(isCalendar);
            btnNewCalendarEvent.setManaged(isCalendar);
        }

        boolean isResources = "RESOURCES".equals(tab);
        if (btnNewResource != null) {
            btnNewResource.setVisible(isResources);
            btnNewResource.setManaged(isResources);
        }

        if (tabPurposeLabel != null) {
            if (isConversation) {
                tabPurposeLabel.setText("Conversación comunitaria · Mensajes en orden cronológico y preguntas académicas con respuestas y soluciones aceptadas.");
                tabPurposeLabel.setVisible(true);
                tabPurposeLabel.setManaged(true);
            } else {
                tabPurposeLabel.setVisible(false);
                tabPurposeLabel.setManaged(false);
            }
        }

        switch (tab) {
            case "OVERVIEW" -> loadOverviewTab();
            case "POSTS" -> loadConversationTab();
            case "RESOURCES" -> loadResourcesTab();
            case "CALENDAR" -> loadCalendarTab();
            case "MEMBERS" -> loadMembersTab();
        }
    }

    // =========================================================================
    // 0. PESTAÑA: PRESENTACIÓN (INICIO DE COMUNIDAD)
    // =========================================================================

    private void loadOverviewTab() {
        long epoch = scope.generation();
        tabStatusLabel.setVisible(false);
        tabStatusLabel.setManaged(false);

        if (overviewStatsStatusLabel != null) {
            overviewStatsStatusLabel.setText("Consultando actividad real de la comunidad…");
            overviewStatsStatusLabel.setVisible(true);
            overviewStatsStatusLabel.setManaged(true);
        }
        if (overviewStatsGrid != null) {
            overviewStatsGrid.setVisible(false);
            overviewStatsGrid.setManaged(false);
        }

        workers.submit(() -> {
            try {
                var stats = api.communityStats(community.id());
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    if (stats != null && overviewStatsGrid != null) {
                        int max = community.maxMembers() > 0 ? community.maxMembers() : 50;
                        long realMembers = stats.totalMembers();
                        int realSlots = (int) Math.max(0, max - realMembers);
                        if (overviewMembersCountLabel != null) {
                            overviewMembersCountLabel.setText(realMembers + " / " + max + " integrantes");
                        }
                        if (overviewCapacityBar != null) {
                            overviewCapacityBar.setProgress(Math.min(1.0, (double) realMembers / max));
                        }
                        if (overviewSlotsLabel != null) {
                            overviewSlotsLabel.setText(realSlots == 0
                                    ? "Comunidad llena (0 espacios disponibles)"
                                    : realSlots + (realSlots == 1 ? " espacio disponible" : " espacios disponibles"));
                        }

                        statPostsLabel.setText(String.valueOf(stats.totalPosts()));
                        statQuestionsLabel.setText(String.valueOf(stats.totalQuestions()));
                        statResolvedLabel.setText(String.valueOf(stats.resolvedQuestions()));
                        statResourcesLabel.setText(String.valueOf(stats.totalResources()));
                        statEventsLabel.setText(String.valueOf(stats.upcomingEvents()));

                        overviewStatsStatusLabel.setVisible(false);
                        overviewStatsStatusLabel.setManaged(false);
                        overviewStatsGrid.setVisible(true);
                        overviewStatsGrid.setManaged(true);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    if (overviewStatsStatusLabel != null) {
                        overviewStatsStatusLabel.setText("Resumen de actividad no disponible en este momento.");
                    }
                });
            }
        });
    }

    // =========================================================================
    // 1. PESTAÑA UNIFICADA: CONVERSACIÓN (CHAT COMUNITARIO + PREGUNTAS Q&A)
    // =========================================================================

    private void loadConversationTab() {
        long epoch = scope.generation();
        tabStatusLabel.setVisible(true);
        tabStatusLabel.setManaged(true);
        tabStatusLabel.setText("Cargando conversación y preguntas de la comunidad…");

        workers.submit(() -> {
            try {
                var page = api.posts(community.id(), null, null);
                var questions = api.questions(community.id(), null, null, null);
                Map<String, String> avatars = new HashMap<>();
                try {
                    for (var m : api.members(community.id())) {
                        if (m.accountId() != null && m.avatarUrl() != null && !m.avatarUrl().isBlank()) {
                            avatars.put(m.accountId(), m.avatarUrl());
                        }
                    }
                } catch (Exception ignored) {
                }

                List<TimelineEntry> merged = new ArrayList<>();
                for (var p : page.posts()) {
                    merged.add(new TimelineEntry(parseInstant(p.createdAt()), p, null));
                }
                for (var q : questions) {
                    merged.add(new TimelineEntry(parseInstant(q.createdAt()), null, q));
                }
                // Orden cronológico ascendente (antiguos arriba, recientes abajo, como un chat de escritorio)
                merged.sort(Comparator.comparing(TimelineEntry::timestamp));

                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    this.cachedTimeline = merged;
                    this.memberAvatarUrls = avatars;
                    renderConversationTimeline(true);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    tabStatusLabel.setVisible(true);
                    tabStatusLabel.setManaged(true);
                    tabStatusLabel.setText("No se pudo cargar la conversación de la comunidad.");
                });
            }
        });
    }

    private void renderConversationTimeline(boolean scrollToBottom) {
        tabContentPane.getChildren().clear();

        List<TimelineEntry> visible = cachedTimeline.stream()
                .filter(entry -> switch (chatFilter) {
                    case "MESSAGES" -> !entry.isQuestion();
                    case "QUESTIONS" -> entry.isQuestion();
                    case "UNRESOLVED" -> entry.isQuestion() && !entry.question().isResolved();
                    default -> true;
                })
                .toList();

        long totalMsg = cachedTimeline.stream().filter(e -> !e.isQuestion()).count();
        long totalQ = cachedTimeline.stream().filter(TimelineEntry::isQuestion).count();
        long resolvedQ = cachedTimeline.stream().filter(e -> e.isQuestion() && e.question().isResolved()).count();

        if (visible.isEmpty()) {
            tabStatusLabel.setVisible(false);
            tabStatusLabel.setManaged(false);
            var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
            if (cachedTimeline.isEmpty()) {
                empty.controller().set("", "Inicia la conversación en «" + community.name() + "»",
                        "Escribe un mensaje en el área inferior o cambia a modo «Pregunta académica» para plantear una duda.",
                        null, null);
            } else {
                empty.controller().set("", "No hay elementos con este filtro",
                        "Cambia el filtro superior para ver el resto de mensajes o preguntas de la comunidad.",
                        "Ver todo el flujo", this::onFilterAll);
            }
            tabContentPane.getChildren().add(empty.root());
            return;
        }

        tabStatusLabel.setText(visible.size() + " elemento(s) · "
                + totalMsg + " mensaje(s) · " + totalQ + " pregunta(s) (" + resolvedQ + " resuelta(s))");
        tabStatusLabel.setVisible(true);
        tabStatusLabel.setManaged(true);

        for (var entry : visible) {
            var item = ViewLoader.<ChatMessageItemController>load("ChatMessageItem.fxml");
            if (entry.isQuestion()) {
                var q = entry.question();
                item.controller().initQuestion(q, community.id(), memberAvatarUrls.get(q.authorAccountId()),
                        api, scope, workers, this::loadConversationTab);
            } else {
                var p = entry.post();
                item.controller().initPost(p, community.id(), memberAvatarUrls.get(p.authorAccountId()),
                        api, scope, workers, this::loadConversationTab);
            }
            tabContentPane.getChildren().add(item.root());
        }

        if (scrollToBottom && mainScrollPane != null) {
            Platform.runLater(() -> mainScrollPane.setVvalue(1.0));
        }
    }

    private void updateChatFilterButtons() {
        Button active = switch (chatFilter) {
            case "MESSAGES" -> btnFilterMessages;
            case "QUESTIONS" -> btnFilterQuestions;
            case "UNRESOLVED" -> btnFilterUnresolved;
            default -> btnFilterAll;
        };
        UiHelper.setActiveTab(active, btnFilterAll, btnFilterMessages, btnFilterQuestions, btnFilterUnresolved);
    }

    @FXML private void onFilterAll() { chatFilter = "ALL"; updateChatFilterButtons(); renderConversationTimeline(false); }
    @FXML private void onFilterMessages() { chatFilter = "MESSAGES"; updateChatFilterButtons(); renderConversationTimeline(false); }
    @FXML private void onFilterQuestions() { chatFilter = "QUESTIONS"; updateChatFilterButtons(); renderConversationTimeline(false); }
    @FXML private void onFilterUnresolved() { chatFilter = "UNRESOLVED"; updateChatFilterButtons(); renderConversationTimeline(false); }
    @FXML private void onRefreshChat() { loadConversationTab(); }

    @FXML
    private void onModeMessage() {
        setComposerMode(false);
    }

    @FXML
    private void onModeQuestion() {
        setComposerMode(true);
    }

    private void setComposerMode(boolean questionMode) {
        this.questionComposerMode = questionMode;
        if (btnModeMessage != null) {
            btnModeMessage.getStyleClass().removeAll("tab-btn-active", "tab-btn");
            btnModeMessage.getStyleClass().add(questionMode ? "tab-btn" : "tab-btn-active");
        }
        if (btnModeQuestion != null) {
            btnModeQuestion.getStyleClass().removeAll("tab-btn-active", "tab-btn");
            btnModeQuestion.getStyleClass().add(questionMode ? "tab-btn-active" : "tab-btn");
        }
        if (questionMetaRow != null) {
            questionMetaRow.setVisible(questionMode);
            questionMetaRow.setManaged(questionMode);
        }
        if (composerModeHint != null) {
            composerModeHint.setText(questionMode
                    ? "Plantea una duda académica: quedará destacada como Pregunta y permitirá marcar solución aceptada."
                    : "Comparte un mensaje rápido con los integrantes de la comunidad.");
        }
        if (composerBodyArea != null) {
            composerBodyArea.setPromptText(questionMode
                    ? "Describe el contexto de tu duda, ejercicio o concepto a resolver…"
                    : "Escribe un mensaje para la comunidad…");
        }
        if (btnSendComposer != null) {
            btnSendComposer.setText(questionMode ? "Publicar pregunta" : "Enviar mensaje");
        }
        setComposerError(null);
    }

    private void setComposerError(String msg) {
        if (composerStatus == null) return;
        boolean show = msg != null && !msg.isBlank();
        composerStatus.setText(show ? msg : "");
        composerStatus.setVisible(show);
        composerStatus.setManaged(show);
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) return Instant.EPOCH;
        try {
            return Instant.parse(iso);
        } catch (Exception ignored) {
            return Instant.EPOCH;
        }
    }

    @FXML
    private void onNewCommunityQuestion() {
        Stage owner = tabContentPane.getScene() != null ? (Stage) tabContentPane.getScene().getWindow() : null;
        var loader = ViewLoader.<AskQuestionDialogController>load("AskQuestionDialog.fxml");
        var modal = UiHelper.createModalStage(loader.root(), "Formular Pregunta en " + community.name(), owner);
        loader.controller().init(api, workers, community, created -> {
            loadConversationTab();
            UiHelper.showSuccess(owner, "Pregunta Publicada", "'" + created.title() + "' ya está visible en la Conversación.");
        });
        modal.showAndWait();
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
                        empty.controller().set("≡", "Sin archivos o enlaces de recursos",
                                "Aquí se comparten guías, libros recomendados y material de apoyo.", "+ Publicar Recurso", this::onNewCommunityResource);
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
                        empty.controller().set("●", "No hay eventos programados",
                                "No se registran exámenes ni entregas próximas en esta comunidad.", "+ Crear Evento", this::onNewCommunityEvent);
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
                    tabStatusLabel.setText("Error al cargar eventos: " + (e.getMessage() != null ? e.getMessage() : ""));
                });
            }
        });
    }

    @FXML
    private void onNewCommunityResource() {
        Stage owner = tabContentPane.getScene() != null ? (Stage) tabContentPane.getScene().getWindow() : null;
        var loader = ViewLoader.<PublishResourceDialogController>load("PublishResourceDialog.fxml");
        var modal = UiHelper.createModalStage(loader.root(), "Publicar Recurso en " + community.name(), owner);
        loader.controller().init(api, workers, community, item -> {
            loadResourcesTab();
            UiHelper.showSuccess(owner, "Recurso Publicado", "'" + item.title() + "' fue añadido a la biblioteca de la comunidad.");
        });
        modal.showAndWait();
    }

    @FXML
    private void onNewCommunityEvent() {
        Stage owner = tabContentPane.getScene() != null ? (Stage) tabContentPane.getScene().getWindow() : null;
        var loader = ViewLoader.<CreateEventDialogController>load("CreateEventDialog.fxml");
        var modal = UiHelper.createModalStage(loader.root(), "Nuevo Evento en " + community.name(), owner);
        loader.controller().init(api, workers, List.of(community), community, event -> {
            loadCalendarTab();
            UiHelper.showSuccess(owner, "Evento Programado", "'" + event.title() + "' ha sido programado en la comunidad.");
        });
        modal.showAndWait();
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
                    headerView.controller().set(members.size(), community.maxMembers(), isAdmin, this::generateInviteCode);
                    tabContentPane.getChildren().add(headerView.root());

                    for (var m : members) {
                        var memberCard = ViewLoader.<MemberCardController>load("MemberCard.fxml");
                        memberCard.controller().set(m, api, workers);
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

    // =========================================================================
    // 6. SALIR DE LA COMUNIDAD (REUTILIZA remove_community_member EN SERVIDOR)
    // =========================================================================

    @FXML
    private void onLeaveCommunity() {
        Stage owner = tabContentPane.getScene() != null ? (Stage) tabContentPane.getScene().getWindow() : null;
        String vis = community.visibility() != null ? community.visibility().strip().toUpperCase(Locale.ROOT) : "PUBLIC";
        boolean requiresConfirmation = "INVITE_ONLY".equals(vis) || "PRIVATE".equals(vis);

        if (requiresConfirmation) {
            String accessNote = "PRIVATE".equals(vis)
                    ? "Esta es una comunidad privada. Para volver a entrar más adelante necesitarás un nuevo código de invitación."
                    : "Esta es una comunidad solo con invitación. Para volver a entrar más adelante necesitarás un código de invitación válido.";
            boolean confirmed = UiHelper.showConfirm(owner,
                    "Salir de «" + community.name() + "»",
                    accessNote + "\n\nAl salir dejarás de tener acceso a las conversaciones, preguntas y respuestas, recursos, calendario e integrantes de esta comunidad.\n\n¿Deseas abandonar esta comunidad?");
            if (!confirmed) return;
        }

        if (btnLeaveCommunity != null) btnLeaveCommunity.setDisable(true);

        workers.submit(() -> {
            try {
                api.leaveCommunity(community.id());
                Platform.runLater(() -> {
                    UiHelper.showSuccess(owner, "Has salido de la comunidad",
                            "Abandonaste «" + community.name() + "». Dejaste de tener acceso a sus conversaciones, recursos, preguntas, calendario e integrantes.");
                    if (navigation != null) {
                        navigation.onBackToHome();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (btnLeaveCommunity != null) btnLeaveCommunity.setDisable(false);
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage()
                            : "No se pudo completar la salida de la comunidad.";
                    UiHelper.showError(owner, "No se pudo salir de la comunidad", msg);
                });
            }
        });
    }

    @FXML
    private void onPublishPost() {
        String body = composerBodyArea.getText().strip();

        if (questionComposerMode) {
            String title = composerTitleField.getText().strip();
            String subject = composerSubjectField != null ? composerSubjectField.getText().strip() : "";
            if (title.length() < 3 || title.length() > 160 || body.isEmpty() || body.length() > 10000) {
                setComposerError("La pregunta requiere título (3–160 caracteres) y descripción (hasta 10 000).");
                return;
            }
            setComposerBusy(true, "Publicando pregunta…");
            workers.submit(() -> {
                try {
                    api.createQuestion(community.id(), UUID.randomUUID(), title, body,
                            subject.isBlank() ? null : subject, List.of());
                    Platform.runLater(() -> {
                        composerTitleField.clear();
                        if (composerSubjectField != null) composerSubjectField.clear();
                        composerBodyArea.clear();
                        setComposerBusy(false, null);
                        loadConversationTab();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        setComposerBusy(false, "No se pudo publicar la pregunta: " + safeError(ex));
                    });
                }
            });
            return;
        }

        if (body.length() < 3 || body.length() > 10000) {
            setComposerError("Escribe un mensaje de al menos 3 caracteres (máx. 10 000).");
            return;
        }
        String derivedTitle = deriveMessageTitle(body);
        setComposerBusy(true, "Enviando mensaje…");

        workers.submit(() -> {
            try {
                api.createPost(community.id(), UUID.randomUUID(), derivedTitle, body);
                Platform.runLater(() -> {
                    composerBodyArea.clear();
                    setComposerBusy(false, null);
                    loadConversationTab();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setComposerBusy(false, "No se pudo enviar: " + safeError(ex)));
            }
        });
    }

    private void setComposerBusy(boolean busy, String statusMsg) {
        if (composerTitleField != null) composerTitleField.setDisable(busy);
        if (composerSubjectField != null) composerSubjectField.setDisable(busy);
        if (composerBodyArea != null) composerBodyArea.setDisable(busy);
        if (btnSendComposer != null) btnSendComposer.setDisable(busy);
        setComposerError(statusMsg);
    }

    private static String deriveMessageTitle(String body) {
        String firstLine = body.split("\\R", 2)[0].strip();
        if (firstLine.length() < 3) firstLine = body.replaceAll("\\s+", " ").strip();
        if (firstLine.length() > 80) return firstLine.substring(0, 79).strip() + "…";
        return firstLine;
    }

    private static String safeError(Exception ex) {
        return ex.getMessage() != null && !ex.getMessage().isBlank() ? ex.getMessage() : "Error en el servidor.";
    }

    private static String formatCategory(String cat) { return UiHelper.formatCommunityCategory(cat); }
    private static String formatVisibility(String vis) { return UiHelper.formatCommunityVisibility(vis); }
    private static String formatRole(String role) { return UiHelper.formatCommunityRole(role); }

    @FXML private void onBackToHome() { if (navigation != null) navigation.onBackToHome(); }
    @FXML private void onTabOverview() { switchTab("OVERVIEW"); }
    @FXML private void onTabPosts() { switchTab("POSTS"); }
    @FXML private void onTabResources() { switchTab("RESOURCES"); }
    @FXML private void onTabCalendar() { switchTab("CALENDAR"); }
    @FXML private void onTabMembers() { switchTab("MEMBERS"); }
    @FXML private void onOpenModeration() { if (navigation != null) navigation.onOpenModeration(community); }
}
