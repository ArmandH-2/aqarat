package co.syntropyhq.aqarat.util;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * The window icons, applied to every stage the application opens.
 *
 * <p>Signing in, signing out and registering each replace the window rather than
 * reusing it, so setting the icon once on the first stage left every window
 * after it showing the toolkit's default. This exists so no future stage can
 * quietly miss them: {@code AppIcons.apply(stage)} is the only step needed.
 *
 * <p>Windows chooses whichever size it needs for the title bar, the task bar and
 * alt-tab, so all four are offered rather than one scaled copy. They are loaded
 * once and shared; the images are immutable and every stage can hold the same
 * instances.
 */
public final class AppIcons {

    private static final int[] SIZES = {32, 64, 128, 256};
    private static final List<Image> ICONS = load();

    private AppIcons() {
    }

    /** Gives a stage the Aqarat icon in every size Windows might ask for. */
    public static void apply(Stage stage) {
        stage.getIcons().setAll(ICONS);
    }

    private static List<Image> load() {
        List<Image> icons = new ArrayList<>();
        for (int size : SIZES) {
            String path = "/images/logo-" + size + ".png";
            try (var stream = AppIcons.class.getResourceAsStream(path)) {
                if (stream == null) {
                    System.err.println("Aqarat: window icon " + path + " is missing.");
                    continue;
                }
                icons.add(new Image(stream));
            } catch (Exception e) {
                // A missing icon costs the application its title-bar mark and
                // nothing else, so it is reported rather than thrown.
                System.err.println("Aqarat: could not load window icon " + path
                    + " — " + e.getMessage());
            }
        }
        return List.copyOf(icons);
    }
}
