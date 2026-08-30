package co.syntropyhq.aqarat.util;

import java.util.ArrayList;
import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Pos;
import java.util.Optional;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * The two things that are still worth stopping someone for: a decision, and a
 * form.
 *
 * <p>Everything else the application used to open a window for — an outcome, a
 * field that was left empty, a receipt — is reported where it happened. What
 * remains here genuinely blocks: the answer changes what the program does next,
 * so there is nothing sensible to do until it is given.
 *
 * <p>Both shapes are built by this one class so they cannot drift apart. Six
 * dialogs were previously hand-assembled in six controllers, and no two of them
 * agreed on padding, button order, or whether required fields were marked at
 * all. The rules are settled once, here:
 *
 * <ul>
 *   <li>A button names its outcome — "Terminate the contract", never "Yes".
 *       A person reading only the buttons must still know what will happen.</li>
 *   <li>A destructive button is red and is never the default, so pressing
 *       Enter cannot destroy anything.</li>
 *   <li>A form's submit button is disabled until every required field is
 *       filled, so a dialog cannot be submitted into an error it already
 *       knows about.</li>
 * </ul>
 */
public final class Dialogs {

    private Dialogs() {
    }

    /** A decision the person has to make. */
    public static Question ask(String heading) {
        return new Question(heading);
    }

    /** A short form that must be answered before the caller can continue. */
    public static Form form(String heading) {
        return new Form(heading);
    }

    /**
     * One piece of writing the caller cannot proceed without: a reason, a
     * response, an outcome note.
     *
     * <p>Replaces {@code TextInputDialog}, which gives a single-line field for
     * an explanation somebody else will read, and no way to say the field is
     * required until after the OK button has been pressed.
     */
    public static Note note(String heading) {
        return new Note(heading);
    }

    /* ================================================================== */

    /** Shared plumbing: our stylesheet, our icon, our button order. */
    private abstract static class Base<S extends Base<S>> {

        /*
         * A plain modal Stage rather than a Dialog. A DialogPane owns its own
         * button bar, and that bar reports a height that leaves out the padding
         * it is painted with, so every dialog in the application opened a few
         * pixels too short and cut the bottom off its own buttons. Owning the
         * footer removes the problem rather than working around it, and it is
         * what makes the button order the same everywhere.
         */
        final Stage stage = new Stage();
        final VBox content = new VBox(16);
        String eyebrow;
        final String heading;
        String confirmLabel = "Confirm";
        String cancelLabel = "Cancel";
        boolean destructive;
        private boolean confirmed;

        Base(String heading) {
            this.heading = heading;
            content.getStyleClass().add("dialog-content");
        }

        @SuppressWarnings("unchecked")
        S self() {
            return (S) this;
        }

        /** Small brass line above the heading, saying what this is about. */
        public S about(String text) {
            this.eyebrow = text == null ? null : text.toUpperCase();
            return self();
        }

        public S confirm(String label) {
            this.confirmLabel = label;
            return self();
        }

        public S cancel(String label) {
            this.cancelLabel = label;
            return self();
        }

        /** Marks the action as irreversible: red button, and never the default. */
        public S destructive() {
            this.destructive = true;
            return self();
        }

        Label headingLabel() {
            Label label = new Label(heading);
            label.getStyleClass().add("dialog-heading");
            label.setWrapText(true);
            return label;
        }

        Node headingBlock() {
            VBox box = new VBox(4);
            if (eyebrow != null) {
                Label line = new Label(eyebrow);
                line.getStyleClass().add("eyebrow");
                box.getChildren().add(line);
            }
            box.getChildren().add(headingLabel());
            return box;
        }

        /**
         * Shows the dialog and reports whether it was confirmed.
         *
         * <p>Returns false for every other way out — Cancel, Escape, the window
         * close button — so a caller only has to handle "they said yes".
         */
        boolean open(BooleanBinding incomplete) {
            Button cancel = new Button(cancelLabel);
            cancel.getStyleClass().addAll("button", "button-secondary");
            cancel.setOnAction(event -> close(cancel));
            cancel.setCancelButton(true);

            Button confirm = new Button(confirmLabel);
            confirm.getStyleClass().addAll("button",
                destructive ? "button-danger-solid" : "button-primary");
            confirm.setOnAction(event -> {
                confirmed = true;
                close(confirm);
            });
            if (incomplete != null) {
                confirm.disableProperty().bind(incomplete);
            }
            // Enter finishes a form, which is what someone typing expects. It is
            // withheld from a destructive question on purpose: the shortcut that
            // saves a keystroke should not also be the one that cannot be undone.
            confirm.setDefaultButton(!destructive);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            // Insertion order, so Cancel always sits left of the action in every
            // dialog rather than wherever the platform would have put it.
            HBox footer = new HBox(10, spacer, cancel, confirm);
            footer.setAlignment(Pos.CENTER_RIGHT);
            footer.getStyleClass().add("dialog-footer");

            VBox root = new VBox(content, footer);
            root.getStyleClass().add("app-dialog");

            Scene scene = new Scene(root);
            scene.getStylesheets().add(Dialogs.class.getResource("/css/app.css").toExternalForm());
            // A dialog owns its own scene, so it needs its own capture hook to be
            // reviewable at all: Windows cannot screenshot it from outside either.
            SceneCapture.install(scene);

            stage.initModality(Modality.APPLICATION_MODAL);
            Window owner = ownerWindow();
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.setTitle(heading);
            stage.setScene(scene);
            stage.setResizable(false);
            AppIcons.apply(stage);
            stage.showAndWait();
            return confirmed;
        }

        private void close(Button source) {
            ((Stage) source.getScene().getWindow()).close();
        }

        private static Window ownerWindow() {
            for (Window window : Window.getWindows()) {
                if (window.isFocused() && window instanceof Stage) {
                    return window;
                }
            }
            return null;
        }
    }

    /* ================================================================== */

    /** "Terminate this contract?" — one question, two named outcomes. */
    public static final class Question extends Base<Question> {

        private String body;

        private Question(String heading) {
            super(heading);
        }

        /** What actually happens, in the person's terms. Say what is lost. */
        public Question because(String text) {
            this.body = text;
            return this;
        }

        public boolean show() {
            VBox text = new VBox(7, headingBlock());
            if (body != null) {
                Label bodyLabel = new Label(body);
                bodyLabel.getStyleClass().add("dialog-body");
                bodyLabel.setWrapText(true);
                text.getChildren().add(bodyLabel);
            }
            text.setMaxWidth(400);

            HBox row = new HBox(15, badge(), text);
            row.setAlignment(Pos.TOP_LEFT);
            content.getChildren().add(row);
            content.setPrefWidth(460);
            return open(null);
        }

        /* A question carries a mark so it is not mistaken for a form at a
           glance: a warning triangle when the answer cannot be taken back, a
           question mark when it can. */
        private Node badge() {
            FontIcon icon = new FontIcon(destructive ? Feather.ALERT_TRIANGLE : Feather.HELP_CIRCLE);
            icon.getStyleClass().add("dialog-badge-icon");
            StackPane badge = new StackPane(icon);
            badge.getStyleClass().addAll("dialog-badge",
                destructive ? "dialog-badge-danger" : "dialog-badge-neutral");
            return badge;
        }
    }

    /* ================================================================== */

    /** A reason, a response, an outcome note: several lines of prose, required. */
    public static final class Note extends Base<Note> {

        private final javafx.scene.control.TextArea area = new javafx.scene.control.TextArea();
        private String label = "Your note";
        private String note;

        private Note(String heading) {
            super(heading);
            confirmLabel = "Send";
            area.setWrapText(true);
            area.setPrefRowCount(4);
        }

        /** The label above the box. Say who reads it, not just what it is. */
        public Note field(String text) {
            this.label = text;
            return this;
        }

        public Note placeholder(String text) {
            area.setPromptText(text);
            return this;
        }

        /** One quiet line under the heading about what happens next. */
        public Note explaining(String text) {
            this.note = text;
            return this;
        }

        /** The text written, or empty if the person backed out. */
        public Optional<String> show() {
            VBox head = new VBox(6, headingBlock());
            if (note != null) {
                Label noteLabel = new Label(note);
                noteLabel.getStyleClass().add("hint");
                noteLabel.setWrapText(true);
                noteLabel.setMaxWidth(400);
                head.getChildren().add(noteLabel);
            }

            RequiredLabel fieldLabel = new RequiredLabel();
            fieldLabel.setText(label);
            content.getChildren().addAll(head, new VBox(5, fieldLabel, area));
            content.setPrefWidth(420);

            BooleanBinding empty = Bindings.createBooleanBinding(
                () -> area.getText() == null || area.getText().isBlank(), area.textProperty());
            return open(empty) ? Optional.of(area.getText().trim()) : Optional.empty();
        }
    }

    /* ================================================================== */

    /** A handful of fields, blocking, with the mandatory ones marked. */
    public static final class Form extends Base<Form> {

        private final VBox fields = new VBox(14);
        private final List<Control> required = new ArrayList<>();
        private String figure;
        private String note;

        private Form(String heading) {
            super(heading);
            confirmLabel = "Save";
        }

        /**
         * The number this form is about, set in the display serif.
         *
         * <p>A person declaring a payment is answering a figure, not filling in
         * a form; showing it at display size is the difference between the two.
         */
        public Form figure(String text) {
            this.figure = text;
            return this;
        }

        /** One quiet line about what happens after they submit. */
        public Form note(String text) {
            this.note = text;
            return this;
        }

        /** A field the form will not submit without. Carries the red mark. */
        public Form required(String label, Control control) {
            fields.getChildren().add(field(new RequiredLabel(), label, control));
            required.add(control);
            return this;
        }

        public Form optional(String label, Control control) {
            Label plain = new Label();
            plain.getStyleClass().add("label-soft");
            fields.getChildren().add(field(plain, label, control));
            return this;
        }

        /** Anything that is not a field: a hint, a divider, a preview. */
        public Form add(Node node) {
            fields.getChildren().add(node);
            return this;
        }

        public boolean show() {
            VBox head = new VBox(6, headingBlock());
            if (figure != null) {
                Label amount = new Label(figure);
                amount.getStyleClass().add("price-display");
                head.getChildren().add(amount);
            }
            if (note != null) {
                Label noteLabel = new Label(note);
                noteLabel.getStyleClass().add("hint");
                noteLabel.setWrapText(true);
                noteLabel.setMaxWidth(380);
                head.getChildren().add(noteLabel);
            }
            content.getChildren().addAll(head, fields);
            content.setPrefWidth(400);
            return open(incomplete());
        }

        private Node field(Label label, String text, Control control) {
            label.setText(text);
            control.setMaxWidth(Double.MAX_VALUE);
            if (control instanceof Region region) {
                region.setMinHeight(Region.USE_PREF_SIZE);
            }
            return new VBox(5, label, control);
        }

        /*
         * True while any required field is still empty, which is what keeps the
         * submit button disabled. Only the control types the forms actually use
         * are understood; anything else is treated as always filled rather than
         * silently blocking a button nobody can then press.
         */
        private BooleanBinding incomplete() {
            if (required.isEmpty()) {
                return null;
            }
            List<javafx.beans.Observable> watched = new ArrayList<>();
            for (Control control : required) {
                if (control instanceof TextInputControl text) {
                    watched.add(text.textProperty());
                } else if (control instanceof ComboBoxBase<?> combo) {
                    watched.add(combo.valueProperty());
                }
            }
            return Bindings.createBooleanBinding(this::anyEmpty,
                watched.toArray(new javafx.beans.Observable[0]));
        }

        private boolean anyEmpty() {
            for (Control control : required) {
                if (control instanceof TextInputControl text) {
                    if (text.getText() == null || text.getText().isBlank()) {
                        return true;
                    }
                } else if (control instanceof ComboBoxBase<?> combo && combo.getValue() == null) {
                    return true;
                }
            }
            return false;
        }
    }
}
