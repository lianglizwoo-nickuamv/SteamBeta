package com.example.steambeta.client;

import com.example.steambeta.client.ui.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Ventana Principal de SteamBeta Campus:
 * Orquestador principal que desacopla la presentación en componentes especializados
 * (Auth, Perfil, Home de Campus, Detalle de Comunidad con pestañas y Biblioteca Académica).
 */
public final class AcademicWindow {
    private final Stage stage;
    private final BorderPane root = new BorderPane();
    private final SessionScope scope = new SessionScope();
    private final ExecutorService workers = Executors.newFixedThreadPool(4, r -> {
        var t = new Thread(r, "campus-http");
        t.setDaemon(true);
        return t;
    });

    private ApiClient api;
    private ApiClient.Identity identity;
    private boolean closing;

    // Componentes visuales modulares
    private AuthComponent authComponent;

    public AcademicWindow(Stage stage) {
        this.stage = stage;
    }

    public void open() {
        stage.setTitle("SteamBeta Campus · Red Social y Académica Universitaria");
        var scene = new Scene(root, 1180, 780);
        scene.getStylesheets().add(getClass().getResource("academic.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setOnCloseRequest(event -> {
            event.consume();
            closeWindow();
        });

        this.authComponent = new AuthComponent(root, scope, workers, (client, signedIdentity) -> {
            this.api = client;
            this.identity = signedIdentity;
            showCampusHome();
        });

        authComponent.showLogin("", "");
        stage.show();
    }

    // =========================================================================
    // Navegación entre Componentes Modulares
    // =========================================================================

    public void showCampusHome() {
        var home = new CampusHomeComponent(root, scope, workers, api, identity, new CampusHomeComponent.HomeNavigationListener() {
            @Override
            public void onOpenCommunity(ApiClient.Community community) {
                showCommunityDetail(community);
            }

            @Override
            public void onOpenProfile() {
                showProfile();
            }

            @Override
            public void onLogout() {
                logout();
            }

            @Override
            public void onOpenAcademicLibrary() {
                showAcademicLibrary();
            }
        });
        home.show();
    }

    public void showProfile() {
        var profile = new ProfileComponent(root, scope, workers, api, new ProfileComponent.ProfileNavigationListener() {
            @Override
            public void onBackToHome() {
                showCampusHome();
            }

            @Override
            public void onOpenCommunity(ApiClient.Community community) {
                showCommunityDetail(community);
            }
        });
        profile.show();
    }

    public void showCommunityDetail(ApiClient.Community community) {
        var detail = new CommunityDetailComponent(root, scope, workers, api, community, new CommunityDetailComponent.CommunityNavigationListener() {
            @Override
            public void onBackToHome() {
                showCampusHome();
            }

            @Override
            public void onOpenModeration(ApiClient.Community comm) {
                openModeration(comm);
            }
        });
        detail.show();
    }

    // =========================================================================
    // Moderación y Biblioteca Académica (Conservación de lógica existente)
    // =========================================================================

    private void openModeration(ApiClient.Community community) {
        long epoch = scope.reset();
        var current = api;
        root.setBottom(null);

        var backBtn = UiHelper.button("← Volver a la comunidad", "secondary");
        backBtn.setOnAction(e -> showCommunityDetail(community));

        var reportsList = new VBox(12);
        var statusLabel = UiHelper.label("Cargando reportes pendientes de moderación…", "muted");

        var content = UiHelper.stack(14,
                backBtn,
                UiHelper.label("Panel de Moderación: " + community.name(), "title"),
                UiHelper.label("Revisa los reportes de publicaciones y comentarios enviados por los integrantes.", "muted"),
                statusLabel,
                reportsList
        );

        root.setCenter(new ScrollPane(content));

        work(() -> current.reports(community.id()), reports -> {
            if (!scope.current(epoch)) return;
            reportsList.getChildren().clear();
            if (reports.isEmpty()) {
                statusLabel.setText("No hay reportes abiertos pendientes de revisión.");
                return;
            }
            statusLabel.setText("Mostrando " + reports.size() + " reporte(s) abierto(s):");
            for (var report : reports) {
                var card = UiHelper.card(
                        new HBox(8,
                                UiHelper.label("Reportado por: " + report.reporterAlias(), "card-title"),
                                UiHelper.hSpacer(),
                                UiHelper.label(UiHelper.formatDate(report.createdAt()), "muted")
                        ),
                        UiHelper.label("Motivo: " + report.reason(), "field-label"),
                        UiHelper.label("Autor del contenido: " + report.contentAuthorAlias(), "muted"),
                        UiHelper.label(report.content() == null ? "(Contenido ya eliminado u oculto)" : report.content(), "post-body")
                );

                var hideBtn = UiHelper.button("Ocultar y resolver", "danger-btn");
                hideBtn.setOnAction(e -> {
                    work(() -> {
                        current.hideReported(community.id(), report.id(), "Contenido ocultado tras revisión de moderación.");
                        return true;
                    }, ok -> openModeration(community), err -> UiHelper.alert("Error", "No se pudo ocultar el contenido.", Alert.AlertType.ERROR));
                });

                var dismissBtn = UiHelper.button("Descartar reporte", "secondary");
                dismissBtn.setOnAction(e -> {
                    work(() -> {
                        current.resolveReport(community.id(), report.id(), "DISMISSED", "Revisado por moderador: no infringe normas.");
                        return true;
                    }, ok -> openModeration(community), err -> UiHelper.alert("Error", "No se pudo descartar el reporte.", Alert.AlertType.ERROR));
                });

                card.getChildren().add(new HBox(10, hideBtn, dismissBtn));
                reportsList.getChildren().add(card);
            }
        }, err -> {
            if (!scope.current(epoch)) return;
            statusLabel.setText("No tienes permisos de moderación o no se pudieron cargar los reportes.");
        });
    }

    private void showAcademicLibrary() {
        long epoch = scope.reset();
        root.setBottom(null);

        var backBtn = UiHelper.button("← Volver a Campus", "secondary");
        backBtn.setOnAction(e -> showCampusHome());

        var logoutBtn = UiHelper.button("Cerrar sesión", "secondary");
        logoutBtn.setOnAction(e -> logout());

        var top = new HBox(16,
                UiHelper.label("SteamBeta", "brand"),
                UiHelper.label(identity.alias() + "\n" + (identity.universityName() != null ? identity.universityName() : ""), "identity"),
                UiHelper.hSpacer(),
                backBtn,
                logoutBtn
        );
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(16, 24, 16, 24));
        top.getStyleClass().add("topbar");
        root.setTop(top);

        var subjectsList = new ListView<ApiClient.Subject>();
        subjectsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ApiClient.Subject subject, boolean empty) {
                super.updateItem(subject, empty);
                setText(null);
                if (empty || subject == null) {
                    setGraphic(null);
                    return;
                }
                var box = new VBox(4,
                        UiHelper.label(subject.code(), "subject-code"),
                        UiHelper.label(subject.name(), "subject-name"),
                        UiHelper.label(subject.termCode() + " / " + subject.sectionCode(), "muted")
                );
                box.setPadding(new Insets(8));
                setGraphic(box);
            }
        });

        var libraryStatus = UiHelper.label("Cargando materias institucionales…", "muted");
        var refreshBtn = UiHelper.button("Actualizar materias", "secondary");

        var side = UiHelper.stack(12,
                UiHelper.label("Mis Materias", "section-title"),
                subjectsList,
                libraryStatus,
                refreshBtn
        );
        side.setPrefWidth(300);
        side.getStyleClass().add("sidebar");
        VBox.setVgrow(subjectsList, Priority.ALWAYS);
        root.setLeft(side);

        var detailPane = UiHelper.stack(14, UiHelper.label("Selecciona una materia", "title"));
        root.setCenter(new ScrollPane(detailPane));

        subjectsList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                loadSubjectDetail(selected, detailPane);
            }
        });

        Runnable loadLibrary = () -> {
            work(api::library, rows -> {
                subjectsList.getItems().setAll(rows);
                libraryStatus.setText(rows.isEmpty() ? "Sin materias matriculadas." : rows.size() + " materias matriculadas");
                if (!rows.isEmpty()) subjectsList.getSelectionModel().selectFirst();
            }, err -> libraryStatus.setText("Biblioteca institucional opcional no vinculada."));
        };

        refreshBtn.setOnAction(e -> loadLibrary.run());
        loadLibrary.run();
    }

    private void loadSubjectDetail(ApiClient.Subject subject, VBox detailPane) {
        detailPane.getChildren().setAll(UiHelper.label("Cargando información de " + subject.name() + "…", "title"));
        work(() -> api.detail(subject.offeringId()), data -> {
            var summary = data.summary();
            String percent = summary.progressPercent() == null ? "Sin requisitos obligatorios"
                    : summary.progressPercent().stripTrailingZeros().toPlainString() + "%";
            var bar = new ProgressBar(summary.progressPercent() == null ? 0 : summary.progressPercent().doubleValue() / 100);
            bar.setMaxWidth(Double.MAX_VALUE);

            detailPane.getChildren().setAll(
                    UiHelper.label(summary.code(), "subject-code"),
                    UiHelper.label(summary.name(), "title"),
                    UiHelper.label("Período " + summary.termCode() + " / Sección " + summary.sectionCode(), "muted"),
                    UiHelper.label("Progreso de Actividades", "section-title"),
                    UiHelper.label(percent, "progress-number"),
                    bar,
                    UiHelper.label(summary.completedCount() + " de " + summary.requiredCount() + " actividades obligatorias", "muted"),
                    UiHelper.label("Listado de Actividades", "section-title")
            );

            for (var act : data.activities()) {
                var row = UiHelper.card(
                        UiHelper.label((act.completed() ? "✓  " : "○  ") + act.name(), "field-label"),
                        UiHelper.label((act.required() ? "Obligatoria" : "Opcional") + " / " + (act.completed() ? "Completada" : "Pendiente"), "muted")
                );
                detailPane.getChildren().add(row);
            }
        }, err -> detailPane.getChildren().setAll(UiHelper.label("No se pudo cargar el detalle de esta materia.", "error")));
    }

    // =========================================================================
    // Gestión de Sesión y Cierre
    // =========================================================================

    private void logout() {
        var previous = api;
        api = null;
        identity = null;
        if (authComponent != null) {
            authComponent.showLogin("Sesión cerrada correctamente.", "");
        }
        if (previous != null) {
            work(() -> {
                previous.logout();
                return true;
            }, ok -> {}, err -> {});
        }
    }

    private void closeWindow() {
        if (closing) return;
        closing = true;
        scope.reset();
        var previous = api;
        api = null;
        identity = null;

        root.setTop(null);
        root.setLeft(null);
        root.setBottom(null);
        root.setCenter(UiHelper.label("Cerrando sesión de SteamBeta Campus…", "title"));
        root.setDisable(true);

        if (previous == null) {
            workers.shutdownNow();
            stage.close();
            return;
        }

        workers.submit(() -> {
            try {
                previous.logout();
            } catch (Exception ignored) {
                previous.clear();
            } finally {
                Platform.runLater(() -> {
                    workers.shutdownNow();
                    stage.close();
                });
            }
        });
    }

    private <T> void work(Callable<T> operation, Consumer<T> success, Consumer<Throwable> failure) {
        long expected = scope.generation();
        workers.submit(() -> {
            try {
                T result = operation.call();
                Platform.runLater(() -> {
                    if (scope.current(expected)) success.accept(result);
                });
            } catch (Exception error) {
                Platform.runLater(() -> {
                    if (scope.current(expected)) failure.accept(error);
                });
            }
        });
    }
}
