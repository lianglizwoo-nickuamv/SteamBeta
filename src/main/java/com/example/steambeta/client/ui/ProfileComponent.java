package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Componente de Perfil Estudiantil Completo:
 * Permite visualizar y editar la identidad académica declarada, bio, intereses y comunidades.
 */
public final class ProfileComponent {

    public interface ProfileNavigationListener {
        void onBackToHome();
        void onOpenCommunity(ApiClient.Community community);
    }

    private final BorderPane root;
    private final SessionScope scope;
    private final ExecutorService workers;
    private final ApiClient api;
    private final ProfileNavigationListener navigation;

    public ProfileComponent(BorderPane root, SessionScope scope, ExecutorService workers,
                            ApiClient api, ProfileNavigationListener navigation) {
        this.root = root;
        this.scope = scope;
        this.workers = workers;
        this.api = api;
        this.navigation = navigation;
    }

    public void show() {
        long epoch = scope.reset();
        root.setBottom(null);

        var backBtn = UiHelper.button("← Volver a Mis Comunidades", "secondary");
        backBtn.setOnAction(e -> navigation.onBackToHome());

        var container = UiHelper.stack(16, backBtn, UiHelper.label("Cargando tu perfil estudiantil…", "muted"));
        var scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        root.setCenter(scroll);

        execute(api::profile, profile -> {
            if (!scope.current(epoch)) return;
            renderProfile(profile, container, epoch);
        }, err -> {
            if (!scope.current(epoch)) return;
            container.getChildren().setAll(
                    backBtn,
                    UiHelper.label("No se pudo cargar el perfil. Revisa la conexión con el backend.", "error")
            );
        });
    }

    private void renderProfile(ApiClient.Profile profile, VBox container, long epoch) {
        container.getChildren().clear();

        var backBtn = UiHelper.button("← Volver a Mis Comunidades", "secondary");
        backBtn.setOnAction(e -> navigation.onBackToHome());

        // Cabecera de Perfil
        var avatarCircle = new StackPane();
        avatarCircle.setPrefSize(72, 72);
        avatarCircle.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 36; -fx-border-color: #38bdf8; -fx-border-radius: 36; -fx-border-width: 2;");
        String initials = (profile.alias() != null && !profile.alias().isBlank())
                ? profile.alias().substring(0, Math.min(2, profile.alias().length())).toUpperCase() : "ST";
        var avatarText = UiHelper.label(initials, "progress-number");
        avatarCircle.getChildren().add(avatarText);

        var aliasLabel = UiHelper.label(profile.alias() == null ? "Sin alias" : profile.alias(), "title");
        var uniLabel = UiHelper.label(
                (profile.declaredUniversity() != null ? profile.declaredUniversity() : "Universidad no especificada")
                + " · " +
                (profile.declaredCareer() != null ? profile.declaredCareer() : "Carrera no especificada"),
                "muted"
        );
        var disclaimer = UiHelper.label("Datos académicos declarados por el estudiante (sin verificación institucional).", "muted");
        disclaimer.setStyle("-fx-font-size: 11px; -fx-font-style: italic;");

        var profileHeader = new HBox(20, avatarCircle, new VBox(6, aliasLabel, uniLabel, disclaimer));
        profileHeader.setAlignment(Pos.CENTER_LEFT);
        profileHeader.setPadding(new Insets(10, 0, 10, 0));

        // Biografía e Intereses
        var bioCard = UiHelper.card(
                UiHelper.label("Biografía Académica", "section-title"),
                UiHelper.label(profile.bio() == null || profile.bio().isBlank()
                        ? "Aún no has escrito una biografía. Cuéntale a tus compañeros qué estás estudiando o en qué proyectos trabajas."
                        : profile.bio(), "post-body")
        );

        var interestsBox = new FlowPane(8, 8);
        if (profile.academicInterests() != null && !profile.academicInterests().isBlank()) {
            for (String tag : profile.academicInterests().split("[,;]")) {
                String clean = tag.strip();
                if (!clean.isEmpty()) {
                    interestsBox.getChildren().add(UiHelper.badge(clean, "tag-pill"));
                }
            }
        } else {
            interestsBox.getChildren().add(UiHelper.label("Sin intereses académicos registrados.", "muted"));
        }

        var interestsCard = UiHelper.card(
                UiHelper.label("Áreas de Interés y Especialidades", "section-title"),
                interestsBox
        );

        // Comunidades en las que participa
        var communitiesBox = new VBox(10);
        var communitiesCard = UiHelper.card(
                UiHelper.label("Mis Comunidades y Espacios de Aprendizaje", "section-title"),
                communitiesBox
        );

        execute(api::communities, communities -> {
            if (!scope.current(epoch)) return;
            communitiesBox.getChildren().clear();
            if (communities.isEmpty()) {
                communitiesBox.getChildren().add(UiHelper.label("Aún no te has unido a ninguna comunidad.", "muted"));
            } else {
                for (var comm : communities) {
                    var roleBadge = UiHelper.badge(comm.role(), "badge-role");
                    var commRow = new HBox(12,
                            UiHelper.label(comm.name(), "card-title"),
                            roleBadge,
                            UiHelper.hSpacer(),
                            UiHelper.button("Entrar", "primary")
                    );
                    commRow.setAlignment(Pos.CENTER_LEFT);
                    ((Button) commRow.getChildren().get(3)).setOnAction(e -> navigation.onOpenCommunity(comm));
                    communitiesBox.getChildren().add(commRow);
                }
            }
        }, err -> {});

        // Botón de edición de perfil
        var editProfileBtn = UiHelper.button("Editar información de mi perfil", "primary");
        editProfileBtn.setOnAction(e -> showEditDialog(profile, epoch));

        container.getChildren().addAll(
                backBtn,
                profileHeader,
                editProfileBtn,
                bioCard,
                interestsCard,
                communitiesCard
        );
    }

    private void showEditDialog(ApiClient.Profile current, long epoch) {
        var dialog = new Dialog<Boolean>();
        dialog.setTitle("Editar Perfil Estudiantil");
        dialog.setHeaderText("Actualiza tu información académica y biografía");

        var saveBtnType = new ButtonType("Guardar cambios", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        var aliasField = new TextField(current.alias() == null ? "" : current.alias());
        var uniField = new TextField(current.declaredUniversity() == null ? "" : current.declaredUniversity());
        var careerField = new TextField(current.declaredCareer() == null ? "" : current.declaredCareer());
        var bioArea = new TextArea(current.bio() == null ? "" : current.bio());
        bioArea.setWrapText(true);
        bioArea.setPrefRowCount(4);
        var interestsField = new TextField(current.academicInterests() == null ? "" : current.academicInterests());
        interestsField.setPromptText("Ej: Algoritmos, Matemáticas Discretas, Inteligencia Artificial");

        var layout = UiHelper.stack(10,
                UiHelper.label("Alias público (2–60 caracteres):", "field-label"), aliasField,
                UiHelper.label("Universidad declarada (opcional):", "field-label"), uniField,
                UiHelper.label("Carrera declarada (opcional):", "field-label"), careerField,
                UiHelper.label("Biografía (máximo 500 caracteres):", "field-label"), bioArea,
                UiHelper.label("Intereses académicos (separados por coma):", "field-label"), interestsField
        );
        layout.setPrefWidth(450);
        dialog.getDialogPane().setContent(layout);

        var saveBtn = dialog.getDialogPane().lookupButton(saveBtnType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String alias = aliasField.getText().strip();
            if (alias.length() < 2 || alias.length() > 60) {
                UiHelper.alert("Error de validación", "El alias debe tener entre 2 y 60 caracteres.", Alert.AlertType.WARNING);
                event.consume();
                return;
            }
            if (bioArea.getText().length() > 500) {
                UiHelper.alert("Error de validación", "La biografía no puede superar los 500 caracteres.", Alert.AlertType.WARNING);
                event.consume();
                return;
            }

            execute(() -> api.updateProfile(
                    alias,
                    uniField.getText().strip(),
                    careerField.getText().strip(),
                    bioArea.getText().strip(),
                    null,
                    interestsField.getText().strip()
            ), updated -> {
                show(); // Recargar perfil con los nuevos datos
            }, err -> {
                UiHelper.alert("Error al guardar", "No se pudo actualizar el perfil. Comprueba la conexión.", Alert.AlertType.ERROR);
            });
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
