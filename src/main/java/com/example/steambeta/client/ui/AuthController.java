package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.File;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

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

    // Vistas Etapa 2: Presentación Académica (Opcional tras crear la cuenta)
    @FXML private VBox onboardingPane;
    @FXML private ImageView onbAvatarImage;
    @FXML private Label onbAvatarInitials;
    @FXML private Button onbChoosePhotoBtn;
    @FXML private Button onbRemovePhotoBtn;
    @FXML private Label onbPhotoHintLabel;
    @FXML private TextField onbUniField;
    @FXML private TextField onbCareerField;
    @FXML private TextField onbSemesterField;
    @FXML private TextField onbInterestsField;
    @FXML private TextArea onbBioArea;
    @FXML private Button onbSkipBtn;
    @FXML private Button onbSaveBtn;

    // Estado compartido
    @FXML private ProgressIndicator busyIndicator;
    @FXML private Label statusLabel;

    private SessionScope scope;
    private ExecutorService workers;
    private AuthListener listener;
    private final AtomicBoolean authInFlight = new AtomicBoolean(false);

    // Estado de sesión recién creada durante Etapa 2 (Presentación académica)
    private ApiClient pendingClient;
    private ApiClient.Identity pendingIdentity;
    private String pendingAlias;
    private Path selectedOnboardingPhoto;

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
        if (!authInFlight.compareAndSet(false, true)) {
            return;
        }
        String identifier = loginIdentifierField.getText().strip();
        String secret = loginPasswordField.getText();

        if (identifier.isBlank() || secret.isEmpty()) {
            authInFlight.set(false);
            setStatus("Por favor escribe tu identificador/correo y tu contraseña.", "status-error");
            return;
        }

        long epoch = scope.reset();
        final ApiClient client;
        try {
            client = new ApiClient();
        } catch (Exception e) {
            authInFlight.set(false);
            setStatus("Error en la configuración: URL de conexión inválida.", "status-error");
            return;
        }

        setBusy(true);
        setStatus("Verificando credenciales en PostgreSQL…", "status-pending");

        workers.submit(() -> {
            try {
                var identity = client.login(identifier, secret);
                Platform.runLater(() -> {
                    authInFlight.set(false);
                    if (!scope.current(epoch)) {
                        client.clear();
                        setBusy(false);
                        return;
                    }
                    try {
                        listener.onLoginSuccess(client, identity);
                    } catch (Throwable uiErr) {
                        client.clear();
                        setBusy(false);
                        setStatus("Error al abrir el panel principal: " + (uiErr.getMessage() != null ? uiErr.getMessage() : uiErr.getClass().getSimpleName()), "status-error");
                    }
                });
            } catch (Throwable err) {
                Platform.runLater(() -> {
                    authInFlight.set(false);
                    if (!scope.current(epoch)) {
                        client.clear();
                        setBusy(false);
                        return;
                    }
                    client.clear();
                    setBusy(false);
                    setStatus(classifyAuthError(err), "status-error");
                    loginPasswordField.requestFocus();
                });
            }
        });
    }

    private static String classifyAuthError(Throwable err) {
        if (err instanceof ApiClient.Failure f) {
            return switch (f.status()) {
                case 401 -> "Credenciales incorrectas.";
                case 403 -> "Acceso denegado: tu cuenta se encuentra suspendida o deshabilitada.";
                case 500, 502, 503, 504 -> "No se pudo verificar la base de datos.";
                default -> "Respuesta inesperada del servidor (HTTP " + f.status() + ").";
            };
        }
        Throwable cause = err;
        while (cause != null) {
            if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException || cause instanceof UnknownHostException) {
                return "No se pudo conectar con el servidor.";
            }
            if (cause instanceof HttpTimeoutException) {
                return "El servidor tardó demasiado en responder.";
            }
            cause = cause.getCause();
        }
        return "No se pudo conectar con el servidor.";
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
                        setBusy(false);
                        showOnboardingStep(client, finalIdentity, alias);
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

    private void showOnboardingStep(ApiClient client, ApiClient.Identity identity, String alias) {
        this.pendingClient = client;
        this.pendingIdentity = identity;
        this.pendingAlias = alias;
        this.selectedOnboardingPhoto = null;

        loginPane.setVisible(false);
        loginPane.setManaged(false);
        registerPane.setVisible(false);
        registerPane.setManaged(false);
        onboardingPane.setVisible(true);
        onboardingPane.setManaged(true);

        AvatarLoader.showInitials(onbAvatarImage, onbAvatarInitials, alias);
        onbRemovePhotoBtn.setVisible(false);
        onbRemovePhotoBtn.setManaged(false);
        onbPhotoHintLabel.setText("JPG, PNG o WEBP · máximo 5 MB.");
        setStatus("¡Cuenta creada! Completa tu perfil académico o pulsa 'Completar más tarde'.", "status-success");
        Platform.runLater(onbUniField::requestFocus);
    }

    @FXML
    private void onChooseOnboardingPhoto() {
        Stage owner = onboardingPane.getScene() != null ? (Stage) onboardingPane.getScene().getWindow() : null;
        File chosen = AvatarLoader.createAvatarFileChooser().showOpenDialog(owner);
        if (chosen == null) return;
        if (chosen.length() > 5L * 1024L * 1024L) {
            setStatus("La fotografía supera el límite máximo de 5 MB.", "status-error");
            return;
        }
        this.selectedOnboardingPhoto = chosen.toPath();
        AvatarLoader.showLocalPreview(onbAvatarImage, onbAvatarInitials, selectedOnboardingPhoto, 60.0, pendingAlias);
        onbRemovePhotoBtn.setVisible(true);
        onbRemovePhotoBtn.setManaged(true);
        onbPhotoHintLabel.setText("Seleccionada: " + chosen.getName());
        clearStatus();
    }

    @FXML
    private void onRemoveOnboardingPhoto() {
        this.selectedOnboardingPhoto = null;
        AvatarLoader.showInitials(onbAvatarImage, onbAvatarInitials, pendingAlias);
        onbRemovePhotoBtn.setVisible(false);
        onbRemovePhotoBtn.setManaged(false);
        onbPhotoHintLabel.setText("JPG, PNG o WEBP · máximo 5 MB.");
    }

    @FXML
    private void onSkipOnboarding() {
        if (pendingClient != null && pendingIdentity != null) {
            ApiClient c = pendingClient;
            ApiClient.Identity id = pendingIdentity;
            pendingClient = null;
            pendingIdentity = null;
            listener.onLoginSuccess(c, id);
        }
    }

    @FXML
    private void onSaveOnboarding() {
        if (pendingClient == null || pendingIdentity == null) return;

        String uni = onbUniField.getText() == null ? "" : onbUniField.getText().strip();
        String career = onbCareerField.getText() == null ? "" : onbCareerField.getText().strip();
        String semRaw = onbSemesterField.getText() == null ? "" : onbSemesterField.getText().strip();
        String interests = onbInterestsField.getText() == null ? "" : onbInterestsField.getText().strip();
        String bio = onbBioArea.getText() == null ? "" : onbBioArea.getText().strip();

        Integer semester = null;
        if (!semRaw.isBlank()) {
            try {
                semester = Integer.parseInt(semRaw);
                if (semester < 1 || semester > 20) {
                    setStatus("El semestre debe ser un número entre 1 y 20.", "status-error");
                    onbSemesterField.requestFocus();
                    return;
                }
            } catch (NumberFormatException ex) {
                setStatus("El semestre debe ser un número entero entre 1 y 20.", "status-error");
                onbSemesterField.requestFocus();
                return;
            }
        }
        if (bio.length() > 500) {
            setStatus("La biografía no puede superar los 500 caracteres.", "status-error");
            onbBioArea.requestFocus();
            return;
        }

        final Integer finalSem = semester;
        final Path photoToUpload = selectedOnboardingPhoto;
        final ApiClient c = pendingClient;
        final ApiClient.Identity id = pendingIdentity;
        final String alias = pendingAlias != null && !pendingAlias.isBlank() ? pendingAlias : "Estudiante";

        setOnboardingBusy(true);
        setStatus("Guardando tu identidad académica…", "status-pending");

        workers.submit(() -> {
            try {
                boolean hasAcademicData = !uni.isBlank() || !career.isBlank() || finalSem != null
                        || !interests.isBlank() || !bio.isBlank();
                if (hasAcademicData) {
                    c.updateProfile(alias, uni, career, finalSem, bio, null, interests, false);
                }
                if (photoToUpload != null) {
                    c.uploadAvatar(photoToUpload);
                }
                Platform.runLater(() -> {
                    pendingClient = null;
                    pendingIdentity = null;
                    listener.onLoginSuccess(c, id);
                });
            } catch (Throwable err) {
                Platform.runLater(() -> {
                    setOnboardingBusy(false);
                    String msg = err.getMessage() != null && !err.getMessage().isBlank()
                            ? err.getMessage() : "No se pudo guardar la información académica.";
                    setStatus(msg, "status-error");
                });
            }
        });
    }

    private void setOnboardingBusy(boolean busy) {
        busyIndicator.setVisible(busy);
        busyIndicator.setManaged(busy);
        onbSaveBtn.setDisable(busy);
        onbSkipBtn.setDisable(busy);
        onbChoosePhotoBtn.setDisable(busy);
        onbRemovePhotoBtn.setDisable(busy);
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
