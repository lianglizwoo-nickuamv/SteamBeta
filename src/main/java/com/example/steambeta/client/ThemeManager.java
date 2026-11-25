package com.example.steambeta.client;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Gestor centralizado de temas visuales de SteamBeta Campus.
 * Permite alternar entre el tema claro predeterminado (academic.css, intacto)
 * y el tema oscuro opcional (academic-dark.css), persistiendo la preferencia localmente.
 */
public final class ThemeManager {

    public enum Theme {
        LIGHT("LIGHT", "Claro académico (Predeterminado)", "academic.css", "#F4F4F4"),
        DARK("DARK", "Grafito oscuro + Ámbar", "academic-dark.css", "#121212");

        private final String code;
        private final String displayName;
        private final String stylesheetFile;
        private final String sceneFillHex;

        Theme(String code, String displayName, String stylesheetFile, String sceneFillHex) {
            this.code = code;
            this.displayName = displayName;
            this.stylesheetFile = stylesheetFile;
            this.sceneFillHex = sceneFillHex;
        }

        public String code() { return code; }
        public String displayName() { return displayName; }
        public String stylesheetFile() { return stylesheetFile; }
        public String sceneFillHex() { return sceneFillHex; }

        @Override
        public String toString() {
            return displayName;
        }

        public static Theme fromCode(String raw) {
            if (raw == null || raw.isBlank()) return LIGHT;
            for (Theme t : values()) {
                if (t.code.equalsIgnoreCase(raw.strip())) return t;
            }
            return LIGHT;
        }
    }

    private static final String PREF_KEY_THEME = "ui.theme";
    private static final Set<Scene> TRACKED_SCENES = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile Theme currentTheme = loadPersistedTheme();

    private ThemeManager() {}

    public static Theme getCurrentTheme() {
        return currentTheme;
    }

    public static synchronized void setTheme(Theme theme) {
        Theme resolved = theme != null ? theme : Theme.LIGHT;
        currentTheme = resolved;
        savePersistedTheme(resolved);
        Runnable applyAll = () -> {
            synchronized (TRACKED_SCENES) {
                for (Scene scene : TRACKED_SCENES) {
                    if (scene != null) {
                        applyStylesheet(scene, resolved);
                    }
                }
            }
        };
        if (Platform.isFxApplicationThread()) {
            applyAll.run();
        } else {
            try {
                Platform.runLater(applyAll);
            } catch (IllegalStateException ignored) {
                // JavaFX toolkit not started (e.g. headless unit test)
            }
        }
    }

    public static void apply(Scene scene) {
        if (scene == null) return;
        synchronized (TRACKED_SCENES) {
            TRACKED_SCENES.add(scene);
        }
        applyStylesheet(scene, currentTheme);
    }

    public static void reloadFromDisk() {
        currentTheme = loadPersistedTheme();
    }

    public static String stylesheetUrl(Theme theme) {
        Theme resolved = theme != null ? theme : Theme.LIGHT;
        URL res = ThemeManager.class.getResource("/com/example/steambeta/client/" + resolved.stylesheetFile());
        return res != null ? res.toExternalForm() : "";
    }

    private static void applyStylesheet(Scene scene, Theme theme) {
        String lightUrl = stylesheetUrl(Theme.LIGHT);
        String darkUrl = stylesheetUrl(Theme.DARK);
        scene.getStylesheets().remove(lightUrl);
        scene.getStylesheets().remove(darkUrl);
        String targetUrl = stylesheetUrl(theme);
        if (!targetUrl.isEmpty() && !scene.getStylesheets().contains(targetUrl)) {
            scene.getStylesheets().add(targetUrl);
        }
        scene.setFill(Color.web(theme.sceneFillHex()));
    }

    static Path resolvePreferencesPath() {
        String override = System.getProperty("steambeta.prefs.file");
        if (override != null && !override.isBlank()) {
            return Path.of(override.strip());
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "SteamBeta", "preferences.properties");
        }
        String userHome = System.getProperty("user.home", ".");
        return Path.of(userHome, ".steambeta", "preferences.properties");
    }

    private static Theme loadPersistedTheme() {
        try {
            Path path = resolvePreferencesPath();
            if (!Files.exists(path)) return Theme.LIGHT;
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            }
            return Theme.fromCode(props.getProperty(PREF_KEY_THEME));
        } catch (Exception ex) {
            return Theme.LIGHT;
        }
    }

    private static void savePersistedTheme(Theme theme) {
        try {
            Path path = resolvePreferencesPath();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Properties props = new Properties();
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    props.load(in);
                } catch (Exception ignored) {}
            }
            props.setProperty(PREF_KEY_THEME, theme.code());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "SteamBeta Campus local UI preferences");
            }
        } catch (Exception ignored) {
            // Si el directorio no es escribible, el tema sigue funcionando en memoria en la sesión actual
        }
    }
}
