package co.syntropyhq.aqarat;

import co.syntropyhq.aqarat.util.AppIcons;
import co.syntropyhq.aqarat.util.SceneCapture;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class App extends Application {

    /* Bundled so the application looks identical on a machine that has never
       installed either family. Instrument Serif carries display sizes, Inter
       carries everything else; app.css names both. */
    private static final String[] BUNDLED_FONTS = {
        "/fonts/InstrumentSerif-Regular.ttf",
        "/fonts/Inter-Regular.ttf",
        "/fonts/Inter-Medium.ttf",
        "/fonts/Inter-SemiBold.ttf",
        "/fonts/Inter-Bold.ttf"
    };

    @Override

    public void start(Stage stage) throws IOException {
        loadFonts();
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"));
        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setTitle("Aqarat");
        AppIcons.apply(stage);
        stage.setScene(scene);
        SceneCapture.install(scene);
        stage.show();
    }

    /* A missing or unreadable font is a cosmetic failure, not a fatal one:
       the CSS fallback stack (Segoe UI / Georgia) still renders the interface,
       so the application starts and says so on the console rather than dying
       at the splash screen. */
    private void loadFonts() {
        for (String path : BUNDLED_FONTS) {
            try (var stream = getClass().getResourceAsStream(path)) {
                if (stream == null || Font.loadFont(stream, 12) == null) {
                    System.err.println("Aqarat: could not load bundled font " + path
                        + " — falling back to a system face.");
                }
            } catch (IOException e) {
                System.err.println("Aqarat: could not read bundled font " + path
                    + " — falling back to a system face. " + e.getMessage());
            }
        }
    }


    public static void main(String[] args) {
        launch(args);
    }
}
