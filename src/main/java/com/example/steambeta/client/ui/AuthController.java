package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Autenticación (FXML) — SteamBeta Campus:
 * Maneja eventos de inicio de sesión y registro de estudiantes con validación
 * en tiempo real y comunicación asíncrona hacia Spring Boot.
 */
public final class AuthController {

    public interface AuthListener {
        void onLoginSuccess(ApiClient client, ApiClient.Identity identity);
    }

    // Vistas Login
    @FXML private VBox loginPane;
    @FXML private TextField loginIdentifierField;
    @FXML private PasswordField loginPasswordField;
    @FXML private TextField loginPasswordVisField;
    @FXML private Button toggleLoginPassBtn;
    @FXML private Button loginSubmitBtn;

    // Vistas Registro
    @FXML private VBox registerPane;
    @FXML private TextField regAliasField;
    @FXML private TextField regLoginField;
    @FXML private TextField regEmailField;
    @FXML private PasswordField regPasswordField;
    @FXML private TextField regPasswordVisField;
    @FXML private PasswordField regConfirmField;
    @FXML private TextField regConfirmVisField;
    @FXML private Label hintLength;
    @FXML private Label hintMatch;
    @FXML private Label hintBytes;
    @FXML private Label hintEmail;
    @FXML private Button registerSubmitBtn;

    // Estado compartido
    @FXML private ProgressIndicator busyIndicator;
    @FXML private Label statusLabel;

    private SessionScope scope;
    private ExecutorService workers;
    private AuthListener listener;

    @FXML
    public void initialize() {
        // Sincronización bidireccional de contraseñas visibles y ocultas
        loginPasswordVisField.textProperty().bindBidirectional(loginPasswordField.textProperty());
        regPasswordVisField.textProperty().bindBidirectional(regPasswordField.textProperty());
        regConfirmVisField.textProperty().bindBidirectional(regConfirmField.textProperty());

        // Validaciones en tiempo real del registro
        Runnable validate = this::validateLiveRegistration;
        regPasswordField.textProperty().addListener((o, old, n) -> validate.run());
        regConfirmField.textProperty().addListener((o, old, n) -> validate.run());
        regEmailField.textProperty().addListener((o, old, n) -> validate.run());
    }

    public void init(SessionScope scope, ExecutorService workers, AuthListener listener, String notice, String presetLogin) {
        this.scope = scope;
        this.workers = workers;
        this.listener = listener;

        if (presetLogin != null && !presetLogin.isBlank()) {
            loginIdentifierField.setText(presetLogin);
        }

        if (notice != null && !notice.isBlank()) {
            boolean isSuccess = notice.contains("exitosamente") || notice.contains("correctamente");
            setStatus(notice, isSuccess ? "status-success" : "status-error");
        }

        Platform.runLater(loginIdentifierField::requestFocus);
    }

    @FXML
    private void onToggleLoginPassword() {
        boolean show = !loginPasswordVisField.isVisible();
        loginPasswordVisField.setVisible(show);
        loginPasswordVisField.setManaged(show);
        loginPasswordField.setVisible(!show);
        loginPasswordField.setManaged(!show);
        toggleLoginPassBtn.setText(show ? "Ocultar" : "Ver");
    }

    @FXML
    private void onToggleRegPasswords() {
        boolean show = !regPasswordVisField.isVisible();
        regPasswordVisField.setVisible(show);
        regPasswordVisField.setManaged(show);
        regPasswordField.setVisible(!show);
        regPasswordField.setManaged(!show);

        regConfirmVisField.setVisible(show);
        regConfirmVisField.setManaged(show);
        regConfirmField.setVisible(!show);
        regConfirmField.setManaged(!show);
    }

    @FXML
    private void onShowRegister() {
        loginPane.setVisible(false);
        loginPane.setManaged(false);
        registerPane.setVisible(true);
        registerPane.setManaged(true);
        clearStatus();
        Platform.runLater(regAliasField::requestFocus);
    }

    @FXML
    private void onShowLogin() {
        registerPane.setVisible(false);
        registerPane.setManaged(false);
        loginPane.setVisible(true);
        loginPane.setManaged(true);
        clearStatus();
        Platform.runLater(loginIdentifierField::requestFocus);
    }

    @FXML
    private void onLoginSubmit() {
        String identifier = loginIdentifierField.getText().strip();
        String secret = loginPasswordField.getText();

        if (identifier.isBlank() || secret.isEmpty()) {
            setStatus("Por favor escribe tu identificador/correo y tu contraseña.", "status-error");
            return;
        }

        long epoch = scope.reset();
        final ApiClient client;
        try {
            client = new ApiClient();
        } catch (Exception e) {
            setStatus("Error en la configuración: URL de conexión inválida.", "status-error");
            return;
        }

        setBusy(true);
        setStatus("Verificando credenciales en PostgreSQL…", "status-pending");

        workers.submit(() -> {
            try {
                var identity = client.login(identifier, secret);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) {
                        client.clear();
                        return;
                    }
                    listener.onLoginSuccess(client, identity);
                });
            } catch (Throwable err) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    client.clear();
                    setBusy(false);

                    if (err instanceof ApiClient.Failure f) {
                        if (f.status() == 401) {
                            setStatus("Identificador, correo o contraseña incorrectos.", "status-error");
                        } else if (f.status() == 403) {
                            setStatus("Acceso denegado: tu cuenta se encuentra suspendida o deshabilitada.", "status-error");
                        } else if (f.status() == 500) {
                            setStatus("Error interno en el servidor central. Intenta más tarde.", "status-error");
                        } else {
                            setStatus("Respuesta inesperada del servidor (HTTP " + f.status() + ").", "status-error");
                        }
                    } else {
                        setStatus("No se pudo conectar con el servidor backend (puerto 8080).", "status-error");
                    }
                    loginPasswordField.requestFocus();
                });
            }
        });
    }

    @FXML
    private void onRegisterSubmit() {
        String alias = regAliasField.getText().strip();
        String login = regLoginField.getText().strip();
        String email = regEmailField.getText().strip();
        String pass = regPasswordField.getText();
        String confirm = regConfirmField.getText();

        if (alias.length() < 2 || alias.length() > 60) {
            setStatus("El alias debe tener entre 2 y 60 caracteres.", "status-error");
            regAliasField.requestFocus();
            return;
        }
        if (login.length() < 3 || login.length() > 80 || !login.matches("^[a-zA-Z0-9][a-zA-Z0-9._@-]{2,79}$")) {
            setStatus("El identificador debe tener entre 3 y 80 caracteres alfanuméricos.", "status-error");
            regLoginField.requestFocus();
            return;
        }
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            setStatus("Escribe un correo electrónico institucional válido.", "status-error");
            regEmailField.requestFocus();
            return;
        }
        if (pass.codePointCount(0, pass.length()) < 12) {
            setStatus("La contraseña debe tener al menos 12 caracteres.", "status-error");
            regPasswordField.requestFocus();
            return;
        }
        if (pass.getBytes(StandardCharsets.UTF_8).length > 72) {
            setStatus("La contraseña excede el límite seguro de 72 bytes.", "status-error");
            regPasswordField.requestFocus();
            return;
        }
        if (!pass.equals(confirm)) {
            setStatus("Las contraseñas no coinciden.", "status-error");
            regConfirmField.requestFocus();
            return;
        }

        long epoch = scope.reset();
        final ApiClient client;
        try {
            client = new ApiClient();
        } catch (Exception e) {
            setStatus("Error en la configuración: URL inválida.", "status-error");
            return;
        }

        setBusy(true);
        setStatus("Registrando cuenta en PostgreSQL (steam_academico)…", "status-pending");

        workers.submit(() -> {
            try {
                client.register(alias, login, email, pass, confirm);
                ApiClient.Identity identity = null;
                try {
                    identity = client.login(login, pass);
                } catch (Exception ignored) {}

                final ApiClient.Identity finalIdentity = identity;
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) {
                        client.clear();
                        return;
                    }
                    if (finalIdentity != null) {
                        listener.onLoginSuccess(client, finalIdentity);
                    } else {
                        onShowLogin();
                        loginIdentifierField.setText(login);
                        setStatus("¡Cuenta creada con éxito! Ingresa tu contraseña para acceder.", "status-success");
                        setBusy(false);
                    }
                });
            } catch (Throwable err) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    setBusy(false);
                    if (err instanceof ApiClient.Failure f) {
                        if (f.status() == 409) {
                            setStatus("El identificador o correo ya está registrado por otro estudiante.", "status-error");
                        } else if (f.status() == 400) {
                            setStatus("Datos de registro inválidos. Revisa los requisitos de los campos.", "status-error");
                        } else {
                            setStatus("Error al registrar en el servidor (HTTP " + f.status() + ").", "status-error");
                        }
                    } else {
                        setStatus("No se pudo conectar con el servidor backend: " + err.getMessage(), "status-error");
                    }
                });
            }
        });
    }

    private void validateLiveRegistration() {
        String p = regPasswordField.getText();
        String c = regConfirmField.getText();
        String em = regEmailField.getText().strip();
        int len = p.codePointCount(0, p.length());
        int bytes = p.getBytes(StandardCharsets.UTF_8).length;

        updateHint(hintLength, len >= 12, "✓ Mínimo 12 caracteres cumplido (" + len + ")", "• Mínimo 12 caracteres (llevas " + len + ")");
        updateHint(hintMatch, !p.isEmpty() && p.equals(c), "✓ Las contraseñas coinciden", !c.isEmpty() ? "✗ Las contraseñas no coinciden" : "• Las contraseñas deben coincidir");
        updateHint(hintBytes, bytes <= 72, "✓ Longitud máxima en bytes correcta", "✗ Excede el límite de 72 bytes (" + bytes + ")");
        updateHint(hintEmail, em.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"), "✓ Correo con formato válido", !em.isEmpty() ? "✗ Correo inválido" : "• Correo con formato válido (@)");
    }

    private void updateHint(Label label, boolean ok, String okText, String failText) {
        label.getStyleClass().removeAll("hint-neutral", "hint-success", "hint-error");
        if (ok) {
            label.setText(okText);
            label.getStyleClass().add("hint-success");
        } else {
            label.setText(failText);
            label.getStyleClass().add(failText.startsWith("✗") ? "hint-error" : "hint-neutral");
        }
    }

    private void setBusy(boolean busy) {
        busyIndicator.setVisible(busy);
        busyIndicator.setManaged(busy);
        loginSubmitBtn.setDisable(busy);
        registerSubmitBtn.setDisable(busy);
        loginIdentifierField.setDisable(busy);
        loginPasswordField.setDisable(busy);
        loginPasswordVisField.setDisable(busy);
        regAliasField.setDisable(busy);
        regLoginField.setDisable(busy);
        regEmailField.setDisable(busy);
        regPasswordField.setDisable(busy);
        regConfirmField.setDisable(busy);
    }

    private void setStatus(String message, String cssClass) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-pending", "status-error", "status-success");
        statusLabel.getStyleClass().add(cssClass);
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.getStyleClass().removeAll("status-pending", "status-error", "status-success");
    }
}
