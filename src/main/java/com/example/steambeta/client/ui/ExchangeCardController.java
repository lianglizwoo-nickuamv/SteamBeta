package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador de Tarjeta de Intercambio (FXML) — SteamBeta Campus.
 */
public final class ExchangeCardController {

    @FXML private Label typeBadge;
    @FXML private Label catBadge;
    @FXML private Label ownerLabel;
    @FXML private Label titleLabel;
    @FXML private Label descLabel;
    @FXML private Label contactLabel;

    public void set(ApiClient.ExchangeItem item) {
        typeBadge.setText(item.exchangeType() != null ? item.exchangeType() : "INTERCAMBIO");
        catBadge.setText(item.category() != null ? item.category() : "GENERAL");
        ownerLabel.setText("Publicado por: " + item.ownerAlias());
        titleLabel.setText(item.title());
        descLabel.setText(item.description() != null ? item.description() : "");
        contactLabel.setText("Contacto: " + (item.contactInfo() != null ? item.contactInfo() : "—"));
    }
}
