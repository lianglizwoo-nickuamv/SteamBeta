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

    @FXML private TextField codeField;
    @FXML private Label statusLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnJoin;

    private ApiClient api;
    private ExecutorService workers;
    private Consumer<ApiClient.Community> onJoined;

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
        this.api = api;
        this.workers = workers;
        this.onJoined = onJoined;
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
        setStatus("Verificando código de acceso en el campus...", false);

        workers.submit(() -> {
            try {
                var comm = api.joinCommunity(code);
                Platform.runLater(() -> {
                    setStatus("¡Te has unido exitosamente a " + comm.name() + "!", false);
                    if (onJoined != null) onJoined.accept(comm);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnJoin.setDisable(false);
                    btnCancel.setDisable(false);
                    setStatus(ex.getMessage(), true);
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
        if (isError) {
            statusLabel.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold;");
        } else {
            statusLabel.setStyle("-fx-text-fill: #34d399; -fx-font-weight: bold;");
        }
    }

    private void close() {
        if (codeField.getScene() != null && codeField.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }
}
