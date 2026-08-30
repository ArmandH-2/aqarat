package co.syntropyhq.aqarat.util;

import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

public final class AlertUtil {

    private AlertUtil() {
    }

    public static void showInfo(String message) {
        show(new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK));
    }

    public static void showError(String message) {
        show(new Alert(Alert.AlertType.ERROR, message, ButtonType.OK));
    }

    public static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        brand(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    private static void show(Alert alert) {
        alert.setHeaderText(null);
        brand(alert);
        alert.showAndWait();
    }

    /**
     * Gives a dialog the application's icon and stylesheet.
     *
     * <p>A JavaFX Alert opens its own stage, which starts with the toolkit's
     * default icon and none of the application's styling — so an error message
     * arrived looking like it came from a different program than the one that
     * raised it.
     */
    private static void brand(Alert alert) {
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        AppIcons.apply(stage);
        alert.getDialogPane().getStylesheets()
            .add(AlertUtil.class.getResource("/css/app.css").toExternalForm());
    }
}
