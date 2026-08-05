package co.syntropyhq.aqarat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"));
        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setTitle("Aqarat");
        stage.getIcons().addAll(loadIcons());
        stage.setScene(scene);
        stage.show();
    }

    // Windows picks whichever size it needs for the title bar, the task bar
    // and alt-tab, so all four are offered rather than one scaled copy.
    private List<Image> loadIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[] {32, 64, 128, 256}) {
            icons.add(new Image(getClass().getResourceAsStream("/images/logo-" + size + ".png")));
        }
        return icons;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
