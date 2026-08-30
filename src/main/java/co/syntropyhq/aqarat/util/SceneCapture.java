package co.syntropyhq.aqarat.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;

/**
 * Writes a PNG of the live scene graph, for design review.
 *
 * <p>Windows cannot screenshot a JavaFX window from the outside: the scene is
 * composited on the GPU, so both {@code CopyFromScreen} and {@code PrintWindow}
 * come back blank or stale. Asking the scene to render itself is the only way to
 * see what a panel actually looks like without a person sitting at the machine.
 *
 * <p>Dormant unless {@code -Daqarat.capture.dir} is set, so a normal run never
 * pays for it and no key is stolen from the interface.
 */
public final class SceneCapture {

    private static final String DIR_PROPERTY = "aqarat.capture.dir";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HHmmss");
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

    private SceneCapture() {
    }

    /** Binds F12 to a capture, and takes one automatically once the scene has settled. */
    public static void install(Scene scene) {
        Path directory = configuredDirectory();
        if (directory == null) {
            return;
        }

        // F12 captures every window that is open, not only the one with focus.
        // A toast and a dialog each live in a window of their own, so capturing
        // just the focused scene photographs the screen without them on it.
        scene.getAccelerators().put(
            new javafx.scene.input.KeyCodeCombination(KeyCode.F12),
            () -> writeAll(directory, "manual"));

        // The first frame is laid out but not yet painted; a short pause lets
        // fonts resolve and any entry animation finish before the shutter.
        PauseTransition settle = new PauseTransition(Duration.millis(1600));
        settle.setOnFinished(event -> write(scene, directory, "startup"));
        settle.play();
    }

    /** Captures immediately under the supplied name. */
    public static void capture(Scene scene, String name) {
        Path directory = configuredDirectory();
        if (directory != null) {
            write(scene, directory, name);
        }
    }

    /* Snapshots every showing window: the shell, whatever dialog is over it, and
       any toast in the corner. Named by index so the order on screen is readable
       from the filenames afterwards. */
    private static void writeAll(Path directory, String name) {
        int index = 0;
        for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
            if (window.isShowing() && window.getScene() != null) {
                write(window.getScene(), directory, name + "-w" + index++);
            }
        }
    }

    private static Path configuredDirectory() {
        String configured = System.getProperty(DIR_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return null;
        }
        return Path.of(configured.trim());
    }

    private static void write(Scene scene, Path directory, String name) {
        try {
            Files.createDirectories(directory);
            WritableImage image = scene.snapshot(null);
            File target = directory.resolve(
                "%02d-%s-%s.png".formatted(
                    SEQUENCE.getAndIncrement(), name, LocalDateTime.now().format(STAMP))).toFile();
            ImageIO.write(toBufferedImage(image), "png", target);
            System.out.println("Aqarat capture: " + target.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Aqarat capture failed: " + e.getMessage());
        }
    }

    /* JavaFX's SwingFXUtils lives in the javafx.swing module, which this project
       does not depend on. Copying the pixels by hand keeps the module list as it is. */
    private static BufferedImage toBufferedImage(WritableImage image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return buffer;
    }
}
