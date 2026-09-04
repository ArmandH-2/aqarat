package co.syntropyhq.aqarat.util;

import javafx.stage.Screen;

/**
 * Sizes the window so a screen recording comes out at an exact pixel size.
 *
 * <p>A film cuts between takes, and two takes only cut together if the window
 * was the same number of physical pixels in both. Dragging a window to size by
 * hand cannot promise that, so the size is asked for rather than aimed at.
 *
 * <p>The number that matters is physical pixels, but a JavaFX scene is measured
 * in logical ones, and Windows separates the two by the display's scale factor.
 * A 125% display turns a 1536-wide scene into 1920 real pixels. This asks the
 * screen for its factor instead of assuming 100%, so the same command produces
 * the same recording on a laptop panel and an external monitor.
 *
 * <p>Dormant unless {@code -Daqarat.capture.size} is set, so a normal run is
 * unaffected:
 *
 * <pre>./mvnw javafx:run -Daqarat.capture.size=1920x1080</pre>
 */
public final class CaptureGeometry {

    private static final String SIZE_PROPERTY = "aqarat.capture.size";

    /** Logical size of the window when no capture size is requested. */
    private static final double DEFAULT_WIDTH = 1280;
    private static final double DEFAULT_HEIGHT = 800;

    private CaptureGeometry() {
    }

    /**
     * The logical scene width to use, in JavaFX units.
     *
     * @return the requested capture width divided by the display scale, or the
     *     default width when no valid capture size has been requested
     */
    public static double sceneWidth() {
        double[] physical = requestedSize();
        return physical == null
            ? DEFAULT_WIDTH
            : physical[0] / Screen.getPrimary().getOutputScaleX();
    }

    /**
     * The logical scene height to use, in JavaFX units.
     *
     * @return the requested capture height divided by the display scale, or the
     *     default height when no valid capture size has been requested
     */
    public static double sceneHeight() {
        double[] physical = requestedSize();
        return physical == null
            ? DEFAULT_HEIGHT
            : physical[1] / Screen.getPrimary().getOutputScaleY();
    }

    /* Returns {width, height} in physical pixels, or null when the property is
       absent or malformed. A typo in a capture flag should not stop the
       application starting, so it says so and falls back to the normal size. */
    private static double[] requestedSize() {
        String value = System.getProperty(SIZE_PROPERTY);
        if (value == null || value.isBlank()) {
            return null;
        }

        String[] parts = value.trim().toLowerCase().split("x");
        if (parts.length != 2) {
            return warnAndIgnore(value);
        }

        try {
            double width = Double.parseDouble(parts[0].trim());
            double height = Double.parseDouble(parts[1].trim());
            if (width <= 0 || height <= 0) {
                return warnAndIgnore(value);
            }
            return new double[] {width, height};
        } catch (NumberFormatException e) {
            return warnAndIgnore(value);
        }
    }

    private static double[] warnAndIgnore(String value) {
        System.err.println("Aqarat: ignoring -D" + SIZE_PROPERTY + "=" + value
            + " — expected a size such as 1920x1080. Using the default window size.");
        return null;
    }
}
