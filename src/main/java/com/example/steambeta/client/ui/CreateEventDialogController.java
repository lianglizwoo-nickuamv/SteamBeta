package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Controlador para la creación de eventos e hitos en comunidades — SteamBeta Campus.
 * Soporta invocación desde el Calendario General (seleccionando comunidad)
 * o desde la pestaña Calendario de una comunidad específica (comunidad fija).
 */
public final class CreateEventDialogController {

    @FXML private ComboBox<ApiClient.Community> communityCombo;
    @FXML private Label communityFixedLabel;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> eventTypeCombo;
    @FXML private DatePicker eventDatePicker;
    @FXML private ComboBox<String> timeCombo;
    @FXML private Label errorLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnSubmit;

    private ApiClient api;
    private ExecutorService workers;
    private Consumer<ApiClient.EventItem> onSuccess;
    private ApiClient.Community fixedCommunity;

    private static final Map<String, String> TYPE_LABELS_TO_ENUM = Map.of(
            "Examen", "EXAM",
            "Entrega", "DELIVERY",
            "Taller", "WORKSHOP",
            "Reunión", "MEETING",
            "Recordatorio", "REMINDER"
    );

    @FXML
    public void initialize() {
        eventTypeCombo.setItems(FXCollections.observableArrayList(
                "Examen", "Entrega", "Taller", "Reunión", "Recordatorio"
        ));
        eventTypeCombo.setValue("Examen");

        timeCombo.setItems(FXCollections.observableArrayList(
                "07:00", "08:00", "09:00", "10:00", "11:00", "12:00",
                "13:00", "14:00", "15:00", "16:00", "17:00", "18:00",
                "19:00", "20:00", "21:00", "22:00"
        ));
        timeCombo.setValue("09:00");

        communityCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(ApiClient.Community c) {
                return c == null ? "" : c.name();
            }

            @Override
            public ApiClient.Community fromString(String string) {
                return null;
            }
        });
    }

    public void init(ApiClient api, ExecutorService workers, List<ApiClient.Community> communities,
                     ApiClient.Community fixedCommunity, Consumer<ApiClient.EventItem> onSuccess) {
        this.api = api;
        this.workers = workers;
        this.fixedCommunity = fixedCommunity;
        this.onSuccess = onSuccess;

        if (fixedCommunity != null) {
            communityCombo.setVisible(false);
            communityCombo.setManaged(false);
            communityFixedLabel.setText("Comunidad: " + fixedCommunity.name());
            communityFixedLabel.setVisible(true);
            communityFixedLabel.setManaged(true);
        } else {
            communityCombo.setItems(FXCollections.observableArrayList(communities));
            if (!communities.isEmpty()) {
                communityCombo.getSelectionModel().selectFirst();
            }
        }
    }

    @FXML
    private void onSubmit() {
        ApiClient.Community targetCommunity = fixedCommunity != null ? fixedCommunity : communityCombo.getValue();
        if (targetCommunity == null) {
            showError("Debes seleccionar una comunidad para asociar el evento.");
            return;
        }

        String title = titleField.getText() == null ? "" : titleField.getText().strip();
        if (title.length() < 3 || title.length() > 160) {
            showError("El título del evento debe tener entre 3 y 160 caracteres.");
            return;
        }

        String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().strip();
        if (description.length() > 2000) {
            showError("La descripción no puede superar 2000 caracteres.");
            return;
        }

        if (eventDatePicker.getValue() == null) {
            showError("Debes seleccionar la fecha del evento.");
            return;
        }

        String rawTime = timeCombo.getValue() != null ? timeCombo.getValue() : "09:00";
        LocalTime localTime;
        try {
            localTime = LocalTime.parse(rawTime);
        } catch (Exception ex) {
            localTime = LocalTime.of(9, 0);
        }

        LocalDate localDate = eventDatePicker.getValue();
        String eventDateIso;
        try {
            ZonedDateTime zdt = localDate.atTime(localTime).atZone(ZoneId.of("America/Managua"));
            eventDateIso = zdt.toInstant().toString();
        } catch (Exception ex) {
            showError("Fecha u hora no válida.");
            return;
        }

        String selectedTypeLabel = eventTypeCombo.getValue() != null ? eventTypeCombo.getValue() : "Examen";
        String eventTypeEnum = TYPE_LABELS_TO_ENUM.getOrDefault(selectedTypeLabel, "EXAM");

        btnSubmit.setDisable(true);
        btnCancel.setDisable(true);
        showInfo("Registrando evento en la comunidad…");

        final String finalCommunityId = targetCommunity.id();
        final String finalTitle = title;
        final String finalDesc = description.isBlank() ? null : description;
        final String finalType = eventTypeEnum;
        final String finalDateIso = eventDateIso;

        workers.submit(() -> {
            try {
                var event = api.createEvent(finalCommunityId, finalTitle, finalDesc, finalType, finalDateIso);
                Platform.runLater(() -> {
                    if (onSuccess != null) onSuccess.accept(event);
                    close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnSubmit.setDisable(false);
                    btnCancel.setDisable(false);
                    String cause = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage()
                            : "Error en el servidor al registrar el evento.";
                    showError("No se pudo programar el evento: " + cause);
                });
            }
        });
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.getStyleClass().setAll("status-error");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void showInfo(String msg) {
        errorLabel.setText(msg);
        errorLabel.getStyleClass().setAll("muted");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void close() {
        if (titleField.getScene() != null && titleField.getScene().getWindow() instanceof Stage s) {
            s.close();
        }
    }
}
