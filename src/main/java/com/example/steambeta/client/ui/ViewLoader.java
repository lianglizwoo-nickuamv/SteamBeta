package com.example.steambeta.client.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.io.IOException;
import java.net.URL;

/**
 * Cargador centralizado de vistas FXML para SteamBeta Campus:
 * Proporciona carga fuertemente tipada de jerarquías FXML y sus controladores asociados.
 */
public final class ViewLoader {

    public record ViewResult<T>(Parent root, T controller) {}

    private ViewLoader() {}

    public static <T> ViewResult<T> load(String fxmlName) {
        String path = "/com/example/steambeta/client/fxml/" + fxmlName;
        URL resource = ViewLoader.class.getResource(path);
        if (resource == null) {
            throw new IllegalStateException("No se encontró el archivo FXML en la ruta: " + path);
        }
        FXMLLoader loader = new FXMLLoader(resource);
        try {
            Parent root = loader.load();
            T controller = loader.getController();
            return new ViewResult<>(root, controller);
        } catch (IOException e) {
            throw new RuntimeException("Error al cargar la vista FXML [" + fxmlName + "]: " + e.getMessage(), e);
        }
    }
}
