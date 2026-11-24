package com.example.steambeta.client;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * Gestión de Entornos de Conexión para SteamBeta Campus:
 * Permite al cliente conectarse a Servidores Centrales (Staging, Producción) o Localhost sin credenciales incrustadas.
 */
public final class EnvironmentConfig {
    public enum Environment {
        LOCAL("Desarrollo Local", "http://127.0.0.1:8080"),
        STAGING("Demostración / Staging", "https://staging.steambeta.campus"),
        PRODUCTION("Servidor Central Campus", "https://api.steambeta.campus");

        private final String displayName;
        private final String defaultUrl;

        Environment(String displayName, String defaultUrl) {
            this.displayName = displayName;
            this.defaultUrl = defaultUrl;
        }

        public String getDisplayName() { return displayName; }
        public String getDefaultUrl() { return defaultUrl; }
    }

    private static Environment currentEnvironment = Environment.LOCAL;
    private static String apiUrl = null;

    static {
        loadConfiguration();
    }

    private EnvironmentConfig() {}

    private static void loadConfiguration() {
        // 1. Variable de Entorno explícita de URL
        String envUrl = System.getenv("STEAMBETA_API_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            apiUrl = sanitizeUrl(envUrl);
            detectEnvironment(apiUrl);
            return;
        }

        // 2. Propiedad de Sistema Java (-Dsteambeta.api.url=...)
        String propUrl = System.getProperty("steambeta.api.url", System.getProperty("replica.api.url"));
        if (propUrl != null && !propUrl.isBlank()) {
            apiUrl = sanitizeUrl(propUrl);
            detectEnvironment(apiUrl);
            return;
        }

        // 3. Archivo de configuración externa steambeta.properties
        File configFile = resolveConfigFile();
        if (configFile != null && configFile.exists()) {
            try (var in = new FileInputStream(configFile)) {
                var props = new Properties();
                props.load(in);
                String fileUrl = props.getProperty("api.url");
                if (fileUrl != null && !fileUrl.isBlank()) {
                    apiUrl = sanitizeUrl(fileUrl);
                    detectEnvironment(apiUrl);
                    return;
                }
            } catch (Exception ignored) {}
        }

        // 4. Fallback por defecto según entorno
        String envName = System.getenv("STEAMBETA_ENV");
        if (envName == null) envName = System.getProperty("steambeta.env", "LOCAL");

        try {
            currentEnvironment = Environment.valueOf(envName.trim().toUpperCase());
        } catch (Exception e) {
            currentEnvironment = Environment.LOCAL;
        }

        apiUrl = currentEnvironment.getDefaultUrl();
    }

    private static File resolveConfigFile() {
        // Buscar en directorio actual
        File currentDir = new File("steambeta.properties");
        if (currentDir.exists()) return currentDir;

        // Buscar en directorio de usuario ~/.steambeta/steambeta.properties o %APPDATA%/SteamBeta
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            File winConfig = new File(appData + File.separator + "SteamBeta" + File.separator + "steambeta.properties");
            if (winConfig.exists()) return winConfig;
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            return new File(userHome + File.separator + ".steambeta" + File.separator + "steambeta.properties");
        }
        return null;
    }

    private static void detectEnvironment(String url) {
        if (url.contains("localhost") || url.contains("127.0.0.1")) {
            currentEnvironment = Environment.LOCAL;
        } else if (url.contains("staging") || url.contains("demo")) {
            currentEnvironment = Environment.STAGING;
        } else {
            currentEnvironment = Environment.PRODUCTION;
        }
    }

    private static String sanitizeUrl(String url) {
        return url.strip().replaceAll("/+$", "");
    }

    public static String getApiUrl() {
        if (apiUrl == null) loadConfiguration();
        return apiUrl;
    }

    public static Environment getCurrentEnvironment() {
        return currentEnvironment;
    }

    public static void setApiUrl(String newUrl) {
        if (newUrl != null && !newUrl.isBlank()) {
            apiUrl = sanitizeUrl(newUrl);
            detectEnvironment(apiUrl);
        }
    }
}
