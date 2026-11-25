package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controlador de Tarjeta de Intercambio (FXML) — SteamBeta Campus.
 */
public final class ExchangeCardController {

    @FXML
    private Label typeBadge;
    @FXML
    private Label catBadge;
    @FXML
    private Label attachBadge;
    @FXML
    private Label ownerLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private Label descLabel;
    @FXML
    private Label contactLabel;
    @FXML
    private Button btnDetails;
    @FXML
    private Button btnEdit;
    @FXML
    private Button btnDelete;

    private Runnable onDetailsAction;
    private Runnable onEditAction;
    private Runnable onDeleteAction;

    public void set(ApiClient.ExchangeItem item) {
        set(item, false, null, null, null);
    }

    public void set(ApiClient.ExchangeItem item, boolean isOwner,
            Runnable onDetails, Runnable onEdit, Runnable onDelete) {
        this.onDetailsAction = onDetails;
        this.onEditAction = onEdit;
        this.onDeleteAction = onDelete;

        typeBadge.setText(PublishMaterialDialogController.typeLabel(item.exchangeType()));
        typeBadge.getStyleClass().removeAll("badge-resolved", "badge-academic", "badge-pending", "badge-role");
        if ("GIFT".equalsIgnoreCase(item.exchangeType())) {
            typeBadge.getStyleClass().add("badge-resolved");
        } else if ("LOAN".equalsIgnoreCase(item.exchangeType())) {
            typeBadge.getStyleClass().add("badge-pending");
        } else {
            typeBadge.getStyleClass().add("badge-academic");
        }

        catBadge.setText(PublishMaterialDialogController.categoryLabel(item.category()));

        String author = item.ownerAlias() != null && !item.ownerAlias().isBlank() ? item.ownerAlias() : "Estudiante";
        String dateText = item.createdAt() != null && !item.createdAt().isBlank()
                ? " · " + UiHelper.formatDateShort(item.createdAt()) : "";
        String availText = "AVAILABLE".equalsIgnoreCase(item.status()) ? " · Disponible" : "";
        ownerLabel.setText("Por " + author + dateText + availText);

        titleLabel.setText(item.title());
        descLabel.setText(item.description() != null ? item.description() : "");
        contactLabel.setText("Contacto: " + (item.contactInfo() != null ? item.contactInfo() : "—"));

        if (attachBadge != null) {
            boolean hasAtt = item.attachmentCount() > 0;
            attachBadge.setVisible(hasAtt);
            attachBadge.setManaged(hasAtt);
            attachBadge.setText(item.attachmentCount() + (item.attachmentCount() == 1 ? " adjunto" : " adjuntos"));
        }
        if (btnEdit != null) {
            btnEdit.setVisible(isOwner && onEdit != null);
            btnEdit.setManaged(isOwner && onEdit != null);
        }
        if (btnDelete != null) {
            btnDelete.setVisible(isOwner && onDelete != null);
            btnDelete.setManaged(isOwner && onDelete != null);
        }
    }

    @FXML
    private void onDetails() {
        if (onDetailsAction != null)
            onDetailsAction.run();
    }

    @FXML
    private void onEdit() {
        if (onEditAction != null)
            onEditAction.run();
    }

    @FXML
    private void onDelete() {
        if (onDeleteAction != null)
            onDeleteAction.run();
    }
}
