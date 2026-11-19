package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Componente modular de autenticación para SteamBeta Campus:
 * Inicio de sesión moderno y registro con validaciones reactivas en vivo.
 */
public final class AuthComponent {

    public interface AuthListener {
        void onLoginSuccess(ApiClient client, ApiClient.Identity identity);
    }

    private final BorderPane root;
    private final SessionScope scope;
    private final ExecutorService workers;
    private final AuthListener listener;

    public AuthComponent(BorderPane root, SessionScope scope, ExecutorService workers, AuthListener listener) {
        this.root = root;
        this.scope = scope;
        this.workers = workers;
        this.listener = listener;
    }

    public void showLogin(String noticeMessage, String presetLogin) {
        scope.reset();
        root.setTop(null);
        root.setLeft(null);
        root.setBottom(null);

        // Panel de Introducción Lateral
        var brand = UiHelper.label("SteamBeta", "brand");
        var badge = UiHelper.badge("CAMPUS", "brand-badge");
        var brandBox = new HBox(10, brand, badge);
        brandBox.setAlignment(Pos.CENTER_LEFT);

        var intro = UiHelper.stack(16,
                brandBox,
                UiHelper.label("Tu red social\ny académica", "display"),
                UiHelper.label("Conecta con comunidades universitarias, resuelve dudas, comparte apuntes y colabora con compañeros de estudio.", "muted"),
                UiHelper.label("No se requiere verificación institucional de matrícula.", "muted")
        );
        intro.getStyleClass().add("intro");
        intro.setPrefWidth(400);
        intro.setAlignment(Pos.CENTER_LEFT);

        // Campos de Acceso
        var loginField = new TextField(presetLogin == null ? "" : presetLogin);
        loginField.setId("loginIdentifier");
        loginField.setPromptText("Identificador de acceso (ej. usuario o correo)");

        // Password con toggle mostrar/ocultar
        var passField = new PasswordField();
        passField.setId("loginPassword");
        passField.setPromptText("Contraseña");
        var passVisibleField = new TextField();
        passVisibleField.setPromptText("Contraseña");
        passVisibleField.setVisible(false);
        passVisibleField.setManaged(false);

        passVisibleField.textProperty().bindBidirectional(passField.textProperty());

        var togglePass = UiHelper.button("Ver", "secondary");
        togglePass.setOnAction(e -> {
            boolean show = !passVisibleField.isVisible();
            passVisibleField.setVisible(show);
            passVisibleField.setManaged(show);
            passField.setVisible(!show);
            passField.setManaged(!show);
            togglePass.setText(show ? "Ocultar" : "Ver");
        });

        var passBox = new HBox(8, new StackPane(passField, passVisibleField), togglePass);
        HBox.setHgrow(passBox.getChildren().get(0), Priority.ALWAYS);

        var enterBtn = UiHelper.button("Iniciar sesión", "primary");
        enterBtn.setId("loginButton");
        enterBtn.setDefaultButton(true);
        enterBtn.setMaxWidth(Double.MAX_VALUE);

        var registerBtn = UiHelper.button("Crear una cuenta nueva", "secondary");
        registerBtn.setId("openRegistration");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setOnAction(e -> showRegister());

        var busy = new ProgressIndicator();
        busy.setPrefSize(24, 24);
        busy.setVisible(false);
        busy.setManaged(false);

        var statusLabel = UiHelper.label(noticeMessage == null ? "" : noticeMessage, "error");
        statusLabel.setId("accessStatus");

        var form = UiHelper.stack(14,
                UiHelper.label("Bienvenido de nuevo", "title"),
                UiHelper.label("Ingresa tus credenciales para acceder al campus.", "muted"),
                UiHelper.label("Identificador de acceso", "field-label"),
                loginField,
                UiHelper.label("Contraseña", "field-label"),
                passBox,
                enterBtn,
                registerBtn,
                new HBox(10, busy, statusLabel)
        );
        form.setMaxWidth(440);
        form.setAlignment(Pos.CENTER_LEFT);

        var center = new HBox(intro, new StackPane(form));
        HBox.setHgrow(center.getChildren().get(1), Priority.ALWAYS);
        root.setCenter(center);

        enterBtn.setOnAction(e -> {
            String identifier = loginField.getText().strip();
            String secret = passField.getText();
            if (identifier.isBlank() || secret.isEmpty()) {
                statusLabel.setText("Por favor escribe tu identificador y contraseña.");
                return;
            }

            long epoch = scope.reset();
            final ApiClient client;
            try {
                client = new ApiClient();
            } catch (Exception ex) {
                statusLabel.setText("No se pudo conectar: revisa la URL del backend.");
                return;
            }

            enterBtn.setDisable(true);
            registerBtn.setDisable(true);
            loginField.setDisable(true);
            passField.setDisable(true);
            passVisibleField.setDisable(true);
            busy.setVisible(true);
            busy.setManaged(true);
            statusLabel.setText("");

            execute(() -> client.login(identifier, secret), identity -> {
                if (!scope.current(epoch)) {
                    client.clear();
                    return;
                }
                listener.onLoginSuccess(client, identity);
            }, err -> {
                if (!scope.current(epoch)) return;
                client.clear();
                enterBtn.setDisable(false);
                registerBtn.setDisable(false);
                loginField.setDisable(false);
                passField.setDisable(false);
                passVisibleField.setDisable(false);
                busy.setVisible(false);
                busy.setManaged(false);
                if (err instanceof ApiClient.Failure f && f.status() == 401) {
                    statusLabel.setText("Identificador o contraseña incorrectos.");
                } else {
                    statusLabel.setText("Error de conexión. Verifica que el servidor backend esté en ejecución.");
                }
                passField.requestFocus();
            });
        });

        Platform.runLater(loginField::requestFocus);
    }

    public void showRegister() {
        long epoch = scope.reset();
        root.setTop(null);
        root.setLeft(null);
        root.setBottom(null);

        var aliasField = new TextField();
        aliasField.setId("registerAlias");
        aliasField.setPromptText("Tu nombre o alias visible en la comunidad");

        var loginField = new TextField();
        loginField.setId("registerLogin");
        loginField.setPromptText("Identificador único para iniciar sesión");

        // Contraseñas con toggle y requisitos reactivos
        var passField = new PasswordField();
        passField.setId("registerPassword");
        passField.setPromptText("Contraseña segura (mínimo 12 caracteres)");
        var passVis = new TextField();
        passVis.setPromptText("Contraseña segura (mínimo 12 caracteres)");
        passVis.setVisible(false);
        passVis.setManaged(false);
        passVis.textProperty().bindBidirectional(passField.textProperty());

        var confirmField = new PasswordField();
        confirmField.setId("registerConfirmation");
        confirmField.setPromptText("Repite tu contraseña exactamente");
        var confirmVis = new TextField();
        confirmVis.setPromptText("Repite tu contraseña exactamente");
        confirmVis.setVisible(false);
        confirmVis.setManaged(false);
        confirmVis.textProperty().bindBidirectional(confirmField.textProperty());

        var toggleBtn = UiHelper.button("Ver contraseñas", "secondary");
        toggleBtn.setOnAction(e -> {
            boolean show = !passVis.isVisible();
            passVis.setVisible(show);
            passVis.setManaged(show);
            passField.setVisible(!show);
            passField.setManaged(!show);
            confirmVis.setVisible(show);
            confirmVis.setManaged(show);
            confirmField.setVisible(!show);
            confirmField.setManaged(!show);
            toggleBtn.setText(show ? "Ocultar" : "Ver contraseñas");
        });

        // Indicadores reactivos en vivo de requisitos
        var reqLength = UiHelper.label("• Mínimo 12 caracteres", "muted");
        var reqMatch = UiHelper.label("• Las contraseñas deben coincidir", "muted");
        var reqBytes = UiHelper.label("• Máximo 72 bytes de longitud", "muted");

        Runnable validatePass = () -> {
            String p = passField.getText();
            String c = confirmField.getText();
            int len = p.codePointCount(0, p.length());
            int bytes = p.getBytes(StandardCharsets.UTF_8).length;

            if (len >= 12) {
                reqLength.getStyleClass().remove("error");
                reqLength.setText("✓ Mínimo 12 caracteres cumplido (" + len + ")");
                reqLength.setStyle("-fx-text-fill: #34d399;");
            } else {
                reqLength.setText("• Mínimo 12 caracteres (llevas " + len + ")");
                reqLength.setStyle("-fx-text-fill: #94a3b8;");
            }

            if (!p.isEmpty() && p.equals(c)) {
                reqMatch.setText("✓ Las contraseñas coinciden");
                reqMatch.setStyle("-fx-text-fill: #34d399;");
            } else if (!c.isEmpty()) {
                reqMatch.setText("✗ Las contraseñas no coinciden");
                reqMatch.setStyle("-fx-text-fill: #f87171;");
            } else {
                reqMatch.setText("• Las contraseñas deben coincidir");
                reqMatch.setStyle("-fx-text-fill: #94a3b8;");
            }

            if (bytes <= 72) {
                reqBytes.setText("✓ Longitud máxima válida");
                reqBytes.setStyle("-fx-text-fill: #34d399;");
            } else {
                reqBytes.setText("✗ Excede el límite de 72 bytes (" + bytes + ")");
                reqBytes.setStyle("-fx-text-fill: #f87171;");
            }
        };

        passField.textProperty().addListener((obs, o, n) -> validatePass.run());
        confirmField.textProperty().addListener((obs, o, n) -> validatePass.run());

        var uniField = new TextField();
        uniField.setPromptText("Tu universidad (opcional, dato declarado)");
        var careerField = new TextField();
        careerField.setPromptText("Tu carrera o especialidad (opcional)");

        var submitBtn = UiHelper.button("Registrarme en SteamBeta Campus", "primary");
        submitBtn.setId("registerButton");
        submitBtn.setMaxWidth(Double.MAX_VALUE);

        var backBtn = UiHelper.button("Ya tengo cuenta: volver al acceso", "secondary");
        backBtn.setMaxWidth(Double.MAX_VALUE);
        backBtn.setOnAction(e -> showLogin("", ""));

        var status = UiHelper.label("", "error");
        status.setId("registrationStatus");

        var busy = new ProgressIndicator();
        busy.setPrefSize(24, 24);
        busy.setVisible(false);
        busy.setManaged(false);

        var formFields = new VBox(10,
                UiHelper.label("Alias público", "field-label"), aliasField,
                UiHelper.label("Identificador de acceso", "field-label"), loginField,
                UiHelper.label("Contraseña", "field-label"),
                new StackPane(passField, passVis),
                UiHelper.label("Confirmar contraseña", "field-label"),
                new StackPane(confirmField, confirmVis),
                new HBox(12, toggleBtn),
                new VBox(4, reqLength, reqMatch, reqBytes),
                new Separator(),
                UiHelper.label("Datos académicos declarados (opcionales)", "field-label"),
                UiHelper.label("Se mostrarán en tu perfil como información declarada por el usuario.", "muted"),
                uniField, careerField
        );

        var form = UiHelper.stack(16,
                UiHelper.label("Crea tu cuenta universitaria", "title"),
                UiHelper.label("Únete a comunidades de estudiantes, colabora y resuelve dudas.", "muted"),
                formFields,
                submitBtn,
                backBtn,
                new HBox(10, busy, status)
        );
        form.setMaxWidth(520);
        form.setAlignment(Pos.CENTER_LEFT);

        var scroll = new ScrollPane(new StackPane(form));
        scroll.setFitToWidth(true);
        root.setCenter(scroll);

        submitBtn.setOnAction(e -> {
            String alias = aliasField.getText().strip();
            String login = loginField.getText().strip();
            String pass = passField.getText();
            String confirm = confirmField.getText();

            if (alias.length() < 2 || alias.length() > 60) {
                status.setText("El alias público debe tener entre 2 y 60 caracteres.");
                return;
            }
            if (login.length() < 3 || login.length() > 80 || !login.matches("^[a-zA-Z0-9][a-zA-Z0-9._@-]{2,79}$")) {
                status.setText("El identificador debe tener entre 3 y 80 caracteres (letras, números, punto, guion o @).");
                return;
            }
            if (pass.codePointCount(0, pass.length()) < 12) {
                status.setText("La contraseña debe tener al menos 12 caracteres.");
                return;
            }
            if (pass.getBytes(StandardCharsets.UTF_8).length > 72) {
                status.setText("La contraseña no debe superar 72 bytes.");
                return;
            }
            if (!pass.equals(confirm)) {
                status.setText("Las contraseñas no coinciden.");
                return;
            }

            final ApiClient client;
            try {
                client = new ApiClient();
            } catch (Exception ex) {
                status.setText("Error en la URL configurada del backend.");
                return;
            }

            formFields.setDisable(true);
            submitBtn.setDisable(true);
            backBtn.setDisable(true);
            busy.setVisible(true);
            busy.setManaged(true);
            status.setText("Creando tu cuenta en Campus…");

            String university = uniField.getText().strip();
            String career = careerField.getText().strip();

            execute(() -> {
                client.register(login, pass, alias, university, career);
                // Intento de auto-login fluido
                return client.login(login, pass);
            }, signedIdentity -> {
                if (!scope.current(epoch)) {
                    client.clear();
                    return;
                }
                // Si el auto-login tuvo éxito, entramos directamente al Campus
                listener.onLoginSuccess(client, signedIdentity);
            }, err -> {
                if (!scope.current(epoch)) return;
                formFields.setDisable(false);
                submitBtn.setDisable(false);
                backBtn.setDisable(false);
                busy.setVisible(false);
                busy.setManaged(false);

                if (err instanceof ApiClient.Failure f && f.status() == 409) {
                    status.setText("El identificador de acceso '" + login + "' ya está registrado por otro estudiante.");
                } else if (err instanceof ApiClient.Failure f && f.status() == 400) {
                    status.setText("Revisa los requisitos de los campos y las longitudes permitidas.");
                } else {
                    // Si se creó pero falló el auto-login, enviamos al login con preset
                    showLogin("¡Cuenta creada exitosamente! Ingresa tu contraseña para acceder.", login);
                }
            });
        });
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
