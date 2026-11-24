package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public final class JoinCommunityDialogController {

    @FXML private Label subtitleLabel;
    @FXML private TextField codeField;
    @FXML private Label statusLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnJoin;

    private ApiClient api;
    private ExecutorService workers;
    private Consumer<ApiClient.Community> onJoined;
    private String expectedCommunityId;

    @FXML
    public void initialize() {
        codeField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String upper = newVal.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
                if (!upper.equals(newVal)) {
                    codeField.setText(upper);
                }
            }
        });
    }

    public void init(ApiClient api, ExecutorService workers, Consumer<ApiClient.Community> onJoined) {
        initForCommunity(api, workers, null, null, onJoined);
    }

    public void initForCommunity(ApiClient api, ExecutorService workers,
                                 String communityId, String communityName,
                                 Consumer<ApiClient.Community> onJoined) {
        this.api = api;
        this.workers = workers;
        this.expectedCommunityId = communityId;
        this.onJoined = onJoined;
        if (subtitleLabel != null && communityName != null && !communityName.isBlank()) {
            subtitleLabel.setText("Introduce el código de invitación válido para ingresar a: " + communityName);
        }
    }

    public String getCode() {
        return codeField.getText() == null ? "" : codeField.getText().strip().toUpperCase(Locale.ROOT);
    }

    @FXML
    private void onJoin() {
        String code = getCode();
        if (code.length() < 6 || code.length() > 16 || !code.matches("^[A-Z0-9]{6,16}$")) {
            setStatus("El código debe contener entre 6 y 16 caracteres alfanuméricos.", true);
            return;
        }

        if (api == null || workers == null) return;

        btnJoin.setDisable(true);
        btnCancel.setDisable(true);
        setStatus("Verificando código de acceso en el servidor...", false);

        workers.submit(() -> {
            try {
                var comm = api.joinCommunity(code, expectedCommunityId);
                Platform.runLater(() -> {
                    setStatus("¡Te has unido exitosamente a " + comm.name() + "!", false);
                    if (onJoined != null) onJoined.accept(comm);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnJoin.setDisable(false);
                    btnCancel.setDisable(false);
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage()
                            : "El código no permite acceder a la comunidad.";
                    setStatus(msg, true);
                });
            }
        });
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().setAll(isError ? "status-error" : "status-ok");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void close() {
        if (codeField.getScene() != null && codeField.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }
}
