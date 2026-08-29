package co.syntropyhq.aqarat.util;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;

/**
 * A field label carrying the red asterisk that marks a required field.
 *
 * <p>Written as a control rather than as two labels in an HBox so that every
 * required field is marked identically and a form cannot drift: the asterisk's
 * colour, size and spacing are defined once. Usable directly from FXML —
 * {@code <RequiredLabel text="Email address"/>} — which keeps the markup no
 * heavier than the plain label it replaces.
 *
 * <p>Mark a field only when the form actually refuses to submit without it. An
 * asterisk on an optional field is worse than none at all.
 */
public class RequiredLabel extends Label {

    public RequiredLabel() {
        Label asterisk = new Label("*");
        asterisk.getStyleClass().add("required-mark");

        setGraphic(asterisk);
        setContentDisplay(ContentDisplay.RIGHT);
        setGraphicTextGap(3);
        getStyleClass().add("label-soft");
    }
}
