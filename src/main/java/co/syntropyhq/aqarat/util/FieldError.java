package co.syntropyhq.aqarat.util;

import javafx.scene.control.Control;
import javafx.scene.control.Label;

public final class FieldError {

    private static final String INVALID_CLASS = "field-invalid";

    private FieldError() {
    }

    // A hidden label still reserves its row unless it is also unmanaged, so
    // visible and managed are always flipped together rather than left to drift.
    public static void show(Control field, Label message, String text) {
        message.setText(text);
        message.setVisible(true);
        message.setManaged(true);
        if (!field.getStyleClass().contains(INVALID_CLASS)) {
            field.getStyleClass().add(INVALID_CLASS);
        }
    }

    public static void clear(Control field, Label message) {
        message.setVisible(false);
        message.setManaged(false);
        field.getStyleClass().remove(INVALID_CLASS);
    }

    public static boolean isShown(Label message) {
        return message.isVisible();
    }
}
