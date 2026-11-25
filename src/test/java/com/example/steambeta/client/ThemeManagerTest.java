package com.example.steambeta.client;

import com.example.steambeta.client.ui.ViewLoader;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ThemeManagerTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
    }

    @Test
    void testDefaultPersistenceInvalidFallbackAndLiveSwitching(@TempDir Path tempDir) throws Exception {
        Path prefsFile = tempDir.resolve("preferences.properties");
        String prevProp = System.getProperty("steambeta.prefs.file");
        System.setProperty("steambeta.prefs.file", prefsFile.toString());
        try {
            // 1. Sin preferencia guardada -> Claro académico (Predeterminado)
            ThemeManager.reloadFromDisk();
            assertEquals(ThemeManager.Theme.LIGHT, ThemeManager.getCurrentTheme());

            // 2. Valor inválido en disco -> fallback seguro a LIGHT
            Files.writeString(prefsFile, "ui.theme=INVALID_THEME\n");
            ThemeManager.reloadFromDisk();
            assertEquals(ThemeManager.Theme.LIGHT, ThemeManager.getCurrentTheme());

            // 3. Seleccionar DARK -> persiste en disco y se recupera tras reiniciar
            ThemeManager.setTheme(ThemeManager.Theme.DARK);
            assertEquals(ThemeManager.Theme.DARK, ThemeManager.getCurrentTheme());
            ThemeManager.reloadFromDisk();
            assertEquals(ThemeManager.Theme.DARK, ThemeManager.getCurrentTheme());

            // 4. Volver a LIGHT -> persiste y restaura exactamente el tema claro
            ThemeManager.setTheme(ThemeManager.Theme.LIGHT);
            assertEquals(ThemeManager.Theme.LIGHT, ThemeManager.getCurrentTheme());
            ThemeManager.reloadFromDisk();
            assertEquals(ThemeManager.Theme.LIGHT, ThemeManager.getCurrentTheme());

            // 5. Actualización inmediata de ventana principal + diálogo abierto y capturas opcionales
            String snapDirPath = System.getProperty("theme.snapshot.dir");
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> err = new AtomicReference<>();

            Platform.runLater(() -> {
                try {
                    Parent mainRoot = ViewLoader.load("MainLayout.fxml").root();
                    Scene mainScene = new Scene(mainRoot, 1240, 820);
                    ThemeManager.apply(mainScene);
                    assertTrue(mainScene.getStylesheets().contains(ThemeManager.stylesheetUrl(ThemeManager.Theme.LIGHT)));

                    Parent dialogRoot = ViewLoader.load("CreateTaskDialog.fxml").root();
                    Scene dialogScene = new Scene(dialogRoot, 600, 520);
                    ThemeManager.apply(dialogScene);
                    assertTrue(dialogScene.getStylesheets().contains(ThemeManager.stylesheetUrl(ThemeManager.Theme.LIGHT)));

                    // Cambiar a DARK -> actualiza ambas escenas inmediatamente
                    ThemeManager.setTheme(ThemeManager.Theme.DARK);
                    assertTrue(mainScene.getStylesheets().contains(ThemeManager.stylesheetUrl(ThemeManager.Theme.DARK)));
                    assertFalse(mainScene.getStylesheets().contains(ThemeManager.stylesheetUrl(ThemeManager.Theme.LIGHT)));
                    assertTrue(dialogScene.getStylesheets().contains(ThemeManager.stylesheetUrl(ThemeManager.Theme.DARK)));

                    if (snapDirPath != null && !snapDirPath.isBlank()) {
                        captureBothThemes(new File(snapDirPath), mainRoot, mainScene, dialogRoot, dialogScene);
                    }

                    // Restaurar LIGHT al finalizar
                    ThemeManager.setTheme(ThemeManager.Theme.LIGHT);
                    assertTrue(mainScene.getStylesheets().contains(ThemeManager.stylesheetUrl(ThemeManager.Theme.LIGHT)));
                    assertTrue(dialogScene.getStylesheets().contains(ThemeManager.stylesheetUrl(ThemeManager.Theme.LIGHT)));
                } catch (Throwable t) {
                    err.set(t);
                } finally {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(60, TimeUnit.SECONDS), "Timeout in JavaFX theme switching test");
            assertNull(err.get(), () -> "JavaFX theme test failed: " + err.get());
        } finally {
            if (prevProp == null) {
                System.clearProperty("steambeta.prefs.file");
            } else {
                System.setProperty("steambeta.prefs.file", prevProp);
            }
            ThemeManager.reloadFromDisk();
        }
    }

    private static void captureBothThemes(File baseDir, Parent mainRoot, Scene mainScene,
                                          Parent dialogRoot, Scene dialogScene) throws Exception {
        baseDir.mkdirs();
        mainRoot.applyCss();
        mainRoot.layout();
        BorderPane contentArea = (BorderPane) mainRoot.lookup("#contentArea");

        Map<String, String[]> screens = new LinkedHashMap<>();
        screens.put("01_dashboard", new String[]{"Home.fxml", "navHome"});
        screens.put("02_comunidades", new String[]{"Communities.fxml", "navCommunities"});
        screens.put("03_equipos_estudio", new String[]{"StudyTeams.fxml", "navStudyTeams"});
        screens.put("04_configuracion", new String[]{"Settings.fxml", "navSettings"});

        for (ThemeManager.Theme theme : new ThemeManager.Theme[]{ThemeManager.Theme.LIGHT, ThemeManager.Theme.DARK}) {
            ThemeManager.setTheme(theme);
            String prefix = theme == ThemeManager.Theme.LIGHT ? "light_" : "dark_";
            Node prevActive = null;
            for (var entry : screens.entrySet()) {
                if (prevActive != null) {
                    prevActive.getStyleClass().remove("nav-item-active");
                    if (!prevActive.getStyleClass().contains("nav-item")) prevActive.getStyleClass().add("nav-item");
                }
                Node navBtn = mainRoot.lookup("#" + entry.getValue()[1]);
                if (navBtn != null) {
                    navBtn.getStyleClass().remove("nav-item");
                    navBtn.getStyleClass().add("nav-item-active");
                    prevActive = navBtn;
                }
                Parent viewRoot = ViewLoader.load(entry.getValue()[0]).root();
                contentArea.setCenter(viewRoot);
                mainRoot.applyCss();
                mainRoot.layout();
                saveSnapshot(mainScene, new File(baseDir, prefix + entry.getKey() + ".png"));
            }
            dialogRoot.applyCss();
            dialogRoot.layout();
            saveSnapshot(dialogScene, new File(baseDir, prefix + "05_dialog_crear_tarea.png"));
        }
    }

    private static void saveSnapshot(Scene scene, File target) throws Exception {
        WritableImage img = scene.snapshot(null);
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        PixelReader pr = img.getPixelReader();

        ByteArrayOutputStream raw = new ByteArrayOutputStream(h * (1 + w * 4));
        for (int y = 0; y < h; y++) {
            raw.write(0);
            for (int x = 0; x < w; x++) {
                int argb = pr.getArgb(x, y);
                raw.write((argb >> 16) & 0xFF);
                raw.write((argb >> 8) & 0xFF);
                raw.write(argb & 0xFF);
                raw.write((argb >> 24) & 0xFF);
            }
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(compressed)) {
            dos.write(raw.toByteArray());
        }
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(target))) {
            out.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream(13);
            DataOutputStream ihdrData = new DataOutputStream(ihdr);
            ihdrData.writeInt(w);
            ihdrData.writeInt(h);
            ihdrData.writeByte(8);
            ihdrData.writeByte(6);
            ihdrData.writeByte(0);
            ihdrData.writeByte(0);
            ihdrData.writeByte(0);
            writeChunk(out, "IHDR", ihdr.toByteArray());
            writeChunk(out, "IDAT", compressed.toByteArray());
            writeChunk(out, "IEND", new byte[0]);
        }
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(data.length);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }
}
