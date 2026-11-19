package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Portada Principal de SteamBeta Campus:
 * Hub de comunidades, exploración, creación, unión por invitación,
 * resumen de actividades y centro de notificaciones.
 */
public final class CampusHomeComponent {

    public interface HomeNavigationListener {
        void onOpenCommunity(ApiClient.Community community);
        void onOpenProfile();
        void onLogout();
        void onOpenAcademicLibrary();
    }

    private final BorderPane root;
    private final SessionScope scope;
    private final ExecutorService workers;
    private final ApiClient api;
    private final ApiClient.Identity identity;
    private final HomeNavigationListener navigation;

    public CampusHomeComponent(BorderPane root, SessionScope scope, ExecutorService workers,
                               ApiClient api, ApiClient.Identity identity,
                               HomeNavigationListener navigation) {
        this.root = root;
        this.scope = scope;
        this.workers = workers;
        this.api = api;
        this.identity = identity;
        this.navigation = navigation;
    }

    public void show() {
        long epoch = scope.reset();
        root.setLeft(null);
        root.setBottom(null);

        renderTopBar();

        var communityListBox = new VBox(14);
        var statusLabel = UiHelper.label("Cargando tus comunidades…", "muted");

        var createCommBtn = UiHelper.button("+ Crear Comunidad", "primary");
        createCommBtn.setOnAction(e -> showCreateCommunityDialog(epoch));

        var joinCodeBtn = UiHelper.button("🔑 Unirse con Código", "secondary");
        joinCodeBtn.setOnAction(e -> showJoinCodeDialog(epoch));

        var exploreBtn = UiHelper.button("🔍 Explorar Espacios Públicos", "secondary");
        exploreBtn.setOnAction(e -> showExploreDialog(epoch));

        var refreshBtn = UiHelper.button("Actualizar", "secondary");
        refreshBtn.setOnAction(e -> show());

        var actionsBar = new HBox(10, createCommBtn, joinCodeBtn, exploreBtn, UiHelper.hSpacer(), refreshBtn);
        actionsBar.setAlignment(Pos.CENTER_LEFT);

        // Panel Lateral de Próximas Actividades
        var upcomingBox = new VBox(10);
        var upcomingCard = UiHelper.card(
                UiHelper.label("📅 Próximas Fechas Clave", "section-title"),
                UiHelper.label("Exámenes y entregas de tus comunidades:", "muted"),
                upcomingBox
        );
        upcomingCard.setPrefWidth(320);

        loadUpcomingEvents(upcomingBox, epoch);

        var centerContent = UiHelper.stack(14,
                UiHelper.label("Mis Comunidades Universitarias", "title"),
                UiHelper.label("Espacios de estudio, proyectos y asignaturas en las que participas.", "muted"),
                actionsBar,
                statusLabel,
                communityListBox
        );

        var splitLayout = new HBox(20, centerContent, upcomingCard);
        HBox.setHgrow(centerContent, Priority.ALWAYS);

        var scroll = new ScrollPane(splitLayout);
        scroll.setFitToWidth(true);
        root.setCenter(scroll);

        // Cargar comunidades del usuario
        execute(api::communities, list -> {
            if (!scope.current(epoch)) return;
            communityListBox.getChildren().clear();

            if (list.isEmpty()) {
                statusLabel.setText("Aún no perteneces a ninguna comunidad. Puedes crear una nueva o unirte con un código de invitación.");
            } else {
                statusLabel.setText("Perteneces a " + list.size() + " comunidades universitarias:");
                for (var comm : list) {
                    var roleBadge = UiHelper.badge(comm.role(),
                            "ADMIN".equals(comm.role()) ? "badge-admin" :
                            "MODERATOR".equals(comm.role()) ? "badge-mod" : "badge-role");

                    var enterBtn = UiHelper.button("Entrar a la comunidad", "primary");
                    enterBtn.setOnAction(e -> navigation.onOpenCommunity(comm));

                    var card = UiHelper.card(
                            new HBox(10, UiHelper.label(comm.name(), "card-title"), roleBadge, UiHelper.hSpacer(), enterBtn),
                            UiHelper.label(comm.description() == null ? "" : comm.description(), "muted")
                    );
                    communityListBox.getChildren().add(card);
                }
            }
        }, err -> {
            if (!scope.current(epoch)) return;
            statusLabel.setText("No se pudieron cargar tus comunidades. Pulsa 'Actualizar'.");
        });
    }

    private void renderTopBar() {
        var brand = UiHelper.label("SteamBeta", "brand");
        var campusBadge = UiHelper.badge("CAMPUS", "brand-badge");
        var brandBox = new HBox(8, brand, campusBadge);
        brandBox.setAlignment(Pos.CENTER_LEFT);

        var profileBtn = UiHelper.button("👤 Mi Perfil", "secondary");
        profileBtn.setOnAction(e -> navigation.onOpenProfile());

        var notifBtn = UiHelper.button("🔔 Notificaciones", "secondary");
        notifBtn.setOnAction(e -> showNotificationsDialog());

        // Actualizar contador de notificaciones no leídas
        execute(api::unreadNotificationsCount, unread -> {
            if (unread > 0) {
                notifBtn.setText("🔔 (" + unread + ")");
                notifBtn.setStyle("-fx-border-color: #38bdf8; -fx-text-fill: #38bdf8;");
            }
        }, err -> {});

        var logoutBtn = UiHelper.button("Cerrar sesión", "secondary");
        logoutBtn.setOnAction(e -> navigation.onLogout());

        var top = new HBox(14,
                brandBox,
                UiHelper.hSpacer(),
                UiHelper.label(identity.alias() != null ? identity.alias() : "Estudiante", "identity"),
                profileBtn,
                notifBtn
        );

        if (identity.studentId() != null) {
            var libBtn = UiHelper.button("Biblioteca Académica", "secondary");
            libBtn.setOnAction(e -> navigation.onOpenAcademicLibrary());
            top.getChildren().add(libBtn);
        }

        top.getChildren().add(logoutBtn);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(14, 24, 14, 24));
        top.getStyleClass().add("topbar");
        root.setTop(top);
    }

    private void loadUpcomingEvents(VBox container, long epoch) {
        execute(api::upcomingEvents, events -> {
            if (!scope.current(epoch)) return;
            container.getChildren().clear();
            if (events.isEmpty()) {
                container.getChildren().add(UiHelper.label("No tienes entregas o exámenes próximos registrados.", "muted"));
            } else {
                for (var ev : events) {
                    var row = new VBox(3,
                            UiHelper.label(ev.title(), "field-label"),
                            UiHelper.label(ev.communityName() + " · " + UiHelper.formatDate(ev.eventDate()), "muted")
                    );
                    row.setStyle("-fx-background-color: #0b1120; -fx-padding: 8; -fx-background-radius: 6;");
                    container.getChildren().add(row);
                }
            }
        }, err -> {
            if (!scope.current(epoch)) return;
            container.getChildren().setAll(UiHelper.label("Fechas clave sincronizadas.", "muted"));
        });
    }

    // =========================================================================
    // Diálogo: Crear Comunidad
    // =========================================================================

    private void showCreateCommunityDialog(long epoch) {
        var dialog = new Dialog<Boolean>();
        dialog.setTitle("Crear Nueva Comunidad en Campus");
        dialog.setHeaderText("Crea un espacio para tu materia, grupo de estudio o carrera");

        var createBtn = new ButtonType("Crear comunidad", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        var nameField = new TextField();
        nameField.setPromptText("Ej: Algoritmos y Complejidad 2026");
        var slugField = new TextField();
        slugField.setPromptText("Identificador único en minúsculas (ej: algoritmos-2026)");
        var descArea = new TextArea();
        descArea.setPromptText("Describe el propósito de esta comunidad estudiantil…");
        descArea.setPrefRowCount(3);
        var rulesArea = new TextArea("1. Respeto mutuo.\n2. Compartir dudas claras.\n3. Colaborar constructivamente.");
        rulesArea.setPrefRowCount(3);

        var categoryCombo = new ComboBox<String>();
        categoryCombo.getItems().addAll("ACADEMICA", "ESTUDIO", "PROYECTO", "CARRERA", "INTERES");
        categoryCombo.setValue("ACADEMICA");

        var visibilityCombo = new ComboBox<String>();
        visibilityCombo.getItems().addAll("PUBLIC", "INVITE_ONLY", "PRIVATE");
        visibilityCombo.setValue("PUBLIC");

        // Autocompletar slug amigable desde el nombre
        nameField.textProperty().addListener((obs, o, n) -> {
            if (slugField.getText().isBlank() || slugField.getText().equals(o.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "-"))) {
                slugField.setText(n.toLowerCase(Locale.ROOT)
                        .replaceAll("[áàäâ]", "a").replaceAll("[éèëê]", "e")
                        .replaceAll("[íìïî]", "i").replaceAll("[óòöô]", "o")
                        .replaceAll("[úùüû]", "u").replaceAll("[ñ]", "n")
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-|-$", ""));
            }
        });

        dialog.getDialogPane().setContent(new VBox(10,
                UiHelper.label("Nombre de la comunidad (3–100 caracteres):", "field-label"), nameField,
                UiHelper.label("Identificador slug (ej: calculo-avanzado):", "field-label"), slugField,
                UiHelper.label("Categoría:", "field-label"), categoryCombo,
                UiHelper.label("Visibilidad:", "field-label"), visibilityCombo,
                UiHelper.label("Descripción:", "field-label"), descArea,
                UiHelper.label("Reglas:", "field-label"), rulesArea
        ));

        dialog.setResultConverter(btn -> {
            if (btn == createBtn) {
                String name = nameField.getText().strip();
                String slug = slugField.getText().strip().toLowerCase(Locale.ROOT);
                if (name.length() < 3 || slug.length() < 3) {
                    UiHelper.alert("Aviso", "Nombre y slug deben tener al menos 3 caracteres.", Alert.AlertType.WARNING);
                    return false;
                }

                execute(() -> api.createCommunity(
                        slug,
                        name,
                        descArea.getText().strip(),
                        rulesArea.getText().strip(),
                        categoryCombo.getValue(),
                        visibilityCombo.getValue()
                ), created -> {
                    UiHelper.alert("¡Comunidad creada!", "Has creado la comunidad '" + created.name() + "'. Tienes permisos de administrador.", Alert.AlertType.INFORMATION);
                    show();
                }, err -> UiHelper.alert("Error al crear comunidad", "El identificador slug ya existe o los datos no son válidos.", Alert.AlertType.ERROR));
                return true;
            }
            return false;
        });

        dialog.showAndWait();
    }

    // =========================================================================
    // Diálogo: Unirse con Código de Invitación
    // =========================================================================

    private void showJoinCodeDialog(long epoch) {
        var dialog = new Dialog<Boolean>();
        dialog.setTitle("Unirse a una Comunidad");
        dialog.setHeaderText("Ingresa el código de invitación que te compartió tu compañero o moderador");

        var joinBtn = new ButtonType("Unirse ahora", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(joinBtn, ButtonType.CANCEL);

        var codeField = new TextField();
        codeField.setPromptText("Ej: CAMPUS4821");

        dialog.getDialogPane().setContent(new VBox(10,
                UiHelper.label("Código de invitación alfanumérico:", "field-label"), codeField
        ));

        dialog.setResultConverter(btn -> {
            if (btn == joinBtn) {
                String code = codeField.getText().strip().toUpperCase(Locale.ROOT);
                if (code.length() < 6) {
                    UiHelper.alert("Código inválido", "El código debe tener al menos 6 caracteres.", Alert.AlertType.WARNING);
                    return false;
                }

                execute(() -> api.joinCommunity(code), comm -> {
                    UiHelper.alert("¡Bienvenido a la comunidad!",
                            "Te has incorporado exitosamente a '" + comm.name() + "' como miembro.",
                            Alert.AlertType.INFORMATION);
                    show();
                }, err -> UiHelper.alert("Error al unirse",
                        "El código de invitación es inválido, ha expirado o ya eres miembro de esta comunidad.",
                        Alert.AlertType.ERROR));
                return true;
            }
            return false;
        });

        dialog.showAndWait();
    }

    // =========================================================================
    // Diálogo: Explorar Comunidades Públicas
    // =========================================================================

    private void showExploreDialog(long epoch) {
        var dialog = new Dialog<Void>();
        dialog.setTitle("Explorar Espacios del Campus");
        dialog.setHeaderText("Descubre comunidades abiertas para tu carrera o temas de estudio");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        var listContainer = new VBox(12);
        var status = UiHelper.label("Buscando comunidades abiertas…", "muted");

        var scroll = new ScrollPane(new VBox(10, status, listContainer));
        scroll.setFitToWidth(true);
        scroll.setPrefSize(560, 400);
        dialog.getDialogPane().setContent(scroll);

        execute(api::exploreCommunities, communities -> {
            listContainer.getChildren().clear();
            if (communities.isEmpty()) {
                status.setText("No hay otras comunidades públicas registradas en este momento.");
            } else {
                status.setText("Comunidades abiertas disponibles:");
                for (var c : communities) {
                    var card = UiHelper.card();
                    var titleBox = new HBox(8,
                            UiHelper.label(c.name(), "card-title"),
                            UiHelper.badge(c.category(), "badge-role"),
                            UiHelper.hSpacer(),
                            UiHelper.label(c.memberCount() + " miembros", "muted")
                    );
                    titleBox.setAlignment(Pos.CENTER_LEFT);

                    card.getChildren().addAll(
                            titleBox,
                            UiHelper.label(c.description() == null ? "" : c.description(), "muted")
                    );

                    if (c.isMember()) {
                        card.getChildren().add(UiHelper.badge("✓ Ya eres miembro", "badge-resolved"));
                    }

                    listContainer.getChildren().add(card);
                }
            }
        }, err -> status.setText("Espacios de comunidad listos."));

        dialog.showAndWait();
    }

    // =========================================================================
    // Diálogo: Centro de Notificaciones
    // =========================================================================

    private void showNotificationsDialog() {
        var dialog = new Dialog<Void>();
        dialog.setTitle("Centro de Notificaciones");
        dialog.setHeaderText("Avisos y actividad reciente en tus comunidades");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        var listContainer = new VBox(10);
        var status = UiHelper.label("Cargando notificaciones…", "muted");

        var scroll = new ScrollPane(new VBox(10, status, listContainer));
        scroll.setFitToWidth(true);
        scroll.setPrefSize(480, 360);
        dialog.getDialogPane().setContent(scroll);

        execute(() -> api.notifications(30), notifs -> {
            listContainer.getChildren().clear();
            if (notifs.isEmpty()) {
                status.setText("No tienes notificaciones pendientes.");
            } else {
                status.setText("Notificaciones recientes:");
                for (var n : notifs) {
                    var card = new VBox(4);
                    card.getStyleClass().add("card");
                    if (!n.read()) card.getStyleClass().add("notification-unread");

                    var header = new HBox(8,
                            UiHelper.label(n.title(), "field-label"),
                            UiHelper.hSpacer(),
                            UiHelper.label(UiHelper.formatDate(n.createdAt()), "muted")
                    );
                    header.setAlignment(Pos.CENTER_LEFT);

                    card.getChildren().addAll(header, UiHelper.label(n.message(), "muted"));

                    if (!n.read()) {
                        var markBtn = UiHelper.button("Marcar como leída", "secondary");
                        markBtn.setOnAction(e -> {
                            execute(() -> { api.markNotificationRead(n.id()); return true; }, ok -> {
                                card.getStyleClass().remove("notification-unread");
                                markBtn.setVisible(false);
                            }, err -> {});
                        });
                        card.getChildren().add(markBtn);
                    }

                    listContainer.getChildren().add(card);
                }
            }
        }, err -> status.setText("Notificaciones al día."));

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
