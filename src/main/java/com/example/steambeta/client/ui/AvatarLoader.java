package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Utilidad compartida para mostrar fotografías de perfil administradas por el servidor
 * con recorte circular y respaldo automático de iniciales cuando no existe foto.
 */
public final class AvatarLoader {

    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    private AvatarLoader() {}

    public static String initialsFor(String alias) {
        if (alias == null || alias.isBlank()) return "ST";
        String clean = alias.strip();
        String[] parts = clean.split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return clean.substring(0, Math.min(2, clean.length())).toUpperCase();
    }

    public static FileChooser createAvatarFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar fotografía de perfil (JPG, PNG o WEBP · máx. 5 MB)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes admitidas (*.jpg, *.jpeg, *.png, *.webp)",
                        "*.jpg", "*.jpeg", "*.png", "*.webp")
        );
        return chooser;
    }

    public static void evictCache(String avatarUrl) {
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            IMAGE_CACHE.remove(avatarUrl);
        }
    }

    public static void showInitials(ImageView imageView, Label initialsLabel, String alias) {
        if (initialsLabel != null) {
            initialsLabel.setText(initialsFor(alias));
            initialsLabel.setVisible(true);
            initialsLabel.setManaged(true);
        }
        if (imageView != null) {
            imageView.setImage(null);
            imageView.setVisible(false);
            imageView.setManaged(false);
        }
    }

    public static void showLocalPreview(ImageView imageView, Label initialsLabel, Path file, double size, String alias) {
        if (file == null || !Files.isRegularFile(file)) {
            showInitials(imageView, initialsLabel, alias);
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            Image img = new Image(new ByteArrayInputStream(bytes), size * 2, size * 2, true, true);
            if (img.isError()) {
                showInitials(imageView, initialsLabel, alias);
                return;
            }
            applyCircularImage(imageView, initialsLabel, img, size);
        } catch (Exception ex) {
            showInitials(imageView, initialsLabel, alias);
        }
    }

    public static void load(ImageView imageView, Label initialsLabel, String alias,
                            String accountId, String avatarUrl, double size,
                            ApiClient api, ExecutorService workers) {
        showInitials(imageView, initialsLabel, alias);
        if (imageView == null || api == null || workers == null
                || accountId == null || accountId.isBlank()
                || avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }

        Image cached = IMAGE_CACHE.get(avatarUrl);
        if (cached != null) {
            applyCircularImage(imageView, initialsLabel, cached, size);
            return;
        }

        imageView.setUserData(avatarUrl);
        workers.submit(() -> {
            try {
                byte[] bytes = api.downloadAvatarBytes(accountId);
                if (bytes == null || bytes.length == 0) return;
                Image img = new Image(new ByteArrayInputStream(bytes), size * 2, size * 2, true, true);
                if (img.isError()) return;
                IMAGE_CACHE.put(avatarUrl, img);
                Platform.runLater(() -> {
                    if (avatarUrl.equals(imageView.getUserData())) {
                        applyCircularImage(imageView, initialsLabel, img, size);
                    }
                });
            } catch (Exception ignored) {
                // Conserva iniciales si la imagen no está disponible
            }
        });
    }

    private static void applyCircularImage(ImageView imageView, Label initialsLabel, Image img, double size) {
        imageView.setImage(img);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        double r = size / 2.0;
        imageView.setClip(new Circle(r, r, r));
        imageView.setVisible(true);
        imageView.setManaged(true);
        if (initialsLabel != null) {
            initialsLabel.setVisible(false);
            initialsLabel.setManaged(false);
        }
    }
}
