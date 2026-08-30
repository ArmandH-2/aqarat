package co.syntropyhq.aqarat.util;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The document a client is given for a payment that has been confirmed.
 *
 * <p>This was three separate strings of newlines shown in an information alert —
 * the same blue-iconed box the application used to report a failure. A receipt
 * is the one artefact a client keeps and may hand to an accountant, so it is
 * built as something that was issued: a masthead, the figure at display size,
 * ruled lines of detail, and the balance the payment leaves behind.
 *
 * <p>It saves as a PDF, drawn by {@link ReceiptPdf} to match what is on screen.
 * A file rather than a print job: JavaFX can print but cannot write, and on
 * Windows it is a printer driver that turns a print job into a file, so a
 * machine with no printer installed had no way to keep a receipt at all.
 * Printing is left to whatever the client opens the file with.
 */
public final class Receipt {

    /* Read by ReceiptPdf, which draws the same document onto a page. Package
       private rather than exposed through eleven getters that exist for one
       caller in the same package. */
    final List<Line> lines = new ArrayList<>();
    final String number;
    final String title;
    BigDecimal amount;
    String statusLabel;
    String statusDetail;
    boolean settled = true;
    BigDecimal remaining;
    String remainingLabel = "Remaining on this contract";
    String footNote =
        "Issued by Aqarat against the proof supplied. Recorded in the audit trail.";

    private final Label saveMessage = new Label();

    private Receipt(String number, String title) {
        this.number = number;
        this.title = title;
    }

    /**
     * Starts a receipt for a payment.
     *
     * <p>The payment's own id is the receipt number. It is already unique, it is
     * already what the audit trail refers to, and inventing a second sequence
     * would give the same payment two names.
     */
    public static Receipt forPayment(int paymentId) {
        return new Receipt(String.format("RCP-%06d", paymentId), "RECEIPT");
    }

    public Receipt amount(BigDecimal value) {
        this.amount = value;
        return this;
    }

    /**
     * The state of the money.
     *
     * @param settled true once an agent has confirmed it — a declared payment is
     *                not yet a receipt for anything, and must not read like one
     */
    public Receipt status(String label, String detail, boolean settled) {
        this.statusLabel = label;
        this.statusDetail = detail;
        this.settled = settled;
        return this;
    }

    /** One ruled line. A null or blank value is dropped rather than shown empty. */
    public Receipt line(String label, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(new Line(label, value));
        }
        return this;
    }

    public Receipt remaining(String label, BigDecimal value) {
        this.remainingLabel = label;
        this.remaining = value;
        return this;
    }

    public Receipt footNote(String text) {
        this.footNote = text;
        return this;
    }

    /** Opens the receipt over the application, modal, with print and close. */
    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        Window owner = focusedWindow();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(title.charAt(0) + title.substring(1).toLowerCase() + " " + number);
        AppIcons.apply(stage);

        VBox document = build();

        Button save = new Button("Save as PDF");
        save.getStyleClass().addAll("button", "button-secondary");
        save.setOnAction(e -> saveAsPdf(stage));

        Button done = new Button("Done");
        done.getStyleClass().addAll("button", "button-primary");
        done.setDefaultButton(true);
        done.setOnAction(e -> stage.close());

        Label foot = new Label(footNote);
        foot.getStyleClass().add("receipt-footnote");
        foot.setWrapText(true);
        foot.setMaxWidth(230);
        HBox.setHgrow(foot, Priority.ALWAYS);

        HBox actions = new HBox(9, foot, save, done);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("receipt-actions");

        // Saving reports itself in the document rather than as a toast, which
        // would rise in a corner of a window this small and cover a button.
        saveMessage.getStyleClass().add("receipt-save-message");
        saveMessage.setWrapText(true);
        // Fills the width the document sets without asking for any of its own:
        // a preferred width taken from the unwrapped text would widen the whole
        // receipt to fit one line of a message.
        saveMessage.setMinWidth(0);
        saveMessage.setPrefWidth(1);
        saveMessage.setMaxWidth(Double.MAX_VALUE);
        saveMessage.setVisible(false);
        saveMessage.setManaged(false);

        VBox root = new VBox(document, saveMessage, actions);
        root.getStyleClass().add("receipt-window");

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        SceneCapture.install(scene);
        stage.setResizable(false);
        stage.showAndWait();
    }

    /* ---------------------------------------------------------------- */

    /*
     * Built fresh on each call rather than reused, because printing needs its
     * own copy: scaling the node that is on screen to fit a page would visibly
     * shrink the receipt the person is looking at.
     */
    private VBox build() {
        VBox document = new VBox();
        document.getStyleClass().add("receipt");
        document.setPrefWidth(460);
        document.getChildren().add(masthead());
        document.getChildren().add(figure());
        if (!lines.isEmpty()) {
            document.getChildren().add(detail());
        }
        if (remaining != null) {
            document.getChildren().add(balance());
        }
        return document;
    }

    private Node masthead() {
        Label mark = new Label("Aqarat");
        mark.getStyleClass().add("receipt-mark");
        Label place = new Label("BEIRUT");
        place.getStyleClass().add("receipt-place");

        Label kind = new Label(title);
        kind.getStyleClass().add("receipt-kind");
        Label ref = new Label(number);
        ref.getStyleClass().add("receipt-number");

        VBox words = new VBox(1, mark, place);
        HBox left = new HBox(11, brandMark(30), words);
        left.setAlignment(Pos.CENTER_LEFT);
        VBox right = new VBox(2, kind, ref);
        right.setAlignment(Pos.TOP_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox head = new HBox(left, spacer, right);
        head.getStyleClass().add("receipt-masthead");
        head.setAlignment(Pos.TOP_LEFT);
        return head;
    }

    /* The light cut of the mark, for the masthead's dark ground. Missing art
       costs the receipt its mark and nothing else, so it is skipped rather than
       thrown. */
    private static Node brandMark(double height) {
        try (var stream = Receipt.class.getResourceAsStream("/images/mark-light-256.png")) {
            if (stream == null) {
                return new Region();
            }
            ImageView view = new ImageView(new Image(stream));
            view.setPreserveRatio(true);
            view.setFitHeight(height);
            return view;
        } catch (IOException e) {
            System.err.println("Aqarat: could not load the receipt mark — " + e.getMessage());
            return new Region();
        }
    }

    private Node figure() {
        Label caption = new Label(settled ? "RECEIVED WITH THANKS" : "DECLARED, AWAITING CONFIRMATION");
        caption.getStyleClass().add("receipt-caption");

        Label value = new Label(Format.paymentAmount(amount));
        value.getStyleClass().add("receipt-amount");

        VBox box = new VBox(3, caption, value);
        box.getStyleClass().add("receipt-figure");

        if (statusLabel != null) {
            Label pill = new Label(statusLabel);
            pill.getStyleClass().addAll("pill", settled ? "pill-good" : "pill-warn");
            HBox row = new HBox(8, pill);
            row.setAlignment(Pos.CENTER_LEFT);
            if (statusDetail != null && !statusDetail.isBlank()) {
                Label detail = new Label(statusDetail);
                detail.getStyleClass().add("receipt-status-detail");
                row.getChildren().add(detail);
            }
            box.getChildren().add(row);
        }
        return box;
    }

    private Node detail() {
        VBox box = new VBox();
        box.getStyleClass().add("receipt-detail");
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            Label label = new Label(line.label);
            label.getStyleClass().add("receipt-line-label");
            Label value = new Label(line.value);
            value.getStyleClass().add("receipt-line-value");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(12, label, spacer, value);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("receipt-line");
            if (i == lines.size() - 1) {
                row.getStyleClass().add("receipt-line-last");
            }
            box.getChildren().add(row);
        }
        return box;
    }

    private Node balance() {
        Label label = new Label(remainingLabel);
        label.getStyleClass().add("receipt-balance-label");
        Label value = new Label(Format.paymentAmount(remaining));
        value.getStyleClass().add("receipt-balance-value");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, label, spacer, value);
        row.setAlignment(Pos.BOTTOM_LEFT);
        row.getStyleClass().add("receipt-balance");
        return row;
    }

    /**
     * Writes the receipt to a file the person chooses.
     *
     * <p>A PDF rather than a print job. JavaFX can print but cannot save, and on
     * Windows it is a printer driver that turns a print job into a file — so on
     * a machine with no printer there was no way to keep a receipt at all.
     * Printing is left to whatever they open the file with.
     */
    private void saveAsPdf(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save receipt " + number);
        chooser.setInitialFileName(number + ".pdf");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF document", "*.pdf"));
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return;
        }
        try {
            ReceiptPdf.write(this, target);
        } catch (IOException | RuntimeException e) {
            say("The receipt could not be saved to " + target.getName()
                + ". Choose another folder, or one you have permission to write to.", true);
            return;
        }
        // The name, not the path. A full Windows path wraps to three lines in a
        // window this narrow, and the person just chose the folder themselves.
        say("Saved as " + target.getName(), false);
    }

    /* Shows a line above the buttons and grows the window to fit it, since the
       receipt is fixed-size and would otherwise clip its own message. */
    private void say(String message, boolean failed) {
        saveMessage.setText(message);
        saveMessage.getStyleClass().removeAll("receipt-save-failed", "receipt-save-done");
        saveMessage.getStyleClass().add(failed ? "receipt-save-failed" : "receipt-save-done");
        saveMessage.setVisible(true);
        saveMessage.setManaged(true);
        ((Stage) saveMessage.getScene().getWindow()).sizeToScene();
    }

    private static Window focusedWindow() {
        for (Window window : Window.getWindows()) {
            if (window.isFocused() && window instanceof Stage) {
                return window;
            }
        }
        return null;
    }

    /* Package private so ReceiptPdf can lay the same rows onto a page. */
    record Line(String label, String value) {
    }
}
