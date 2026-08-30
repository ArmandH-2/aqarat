package co.syntropyhq.aqarat.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
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
 * <p>It prints. On Windows, choosing "Microsoft Print to PDF" in the print
 * dialog saves it as a PDF, which is why no PDF library is pulled in for it:
 * the platform already has one, and a dependency that produces a worse-looking
 * page than the one already on screen is not worth carrying.
 */
public final class Receipt {

    private final List<Line> lines = new ArrayList<>();
    private final String number;
    private final String title;
    private BigDecimal amount;
    private String statusLabel;
    private String statusDetail;
    private boolean settled = true;
    private BigDecimal remaining;
    private String remainingLabel = "Remaining on this contract";
    private String footNote =
        "Issued by Aqarat against the proof supplied. Recorded in the audit trail.";

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

        Button print = new Button("Print / Save as PDF");
        print.getStyleClass().addAll("button", "button-secondary");
        print.setOnAction(e -> print(stage));

        Button done = new Button("Done");
        done.getStyleClass().addAll("button", "button-primary");
        done.setDefaultButton(true);
        done.setOnAction(e -> stage.close());

        Label foot = new Label(footNote);
        foot.getStyleClass().add("receipt-footnote");
        foot.setWrapText(true);
        foot.setMaxWidth(230);
        HBox.setHgrow(foot, Priority.ALWAYS);

        HBox actions = new HBox(9, foot, print, done);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("receipt-actions");

        VBox root = new VBox(document, actions);
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

        VBox left = new VBox(1, mark, place);
        VBox right = new VBox(2, kind, ref);
        right.setAlignment(Pos.TOP_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox head = new HBox(left, spacer, right);
        head.getStyleClass().add("receipt-masthead");
        head.setAlignment(Pos.TOP_LEFT);
        return head;
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

    /*
     * Prints a fresh copy of the document, scaled down only as far as the page
     * needs. It is never scaled up: a receipt blown across a sheet of A4 looks
     * like a mistake rather than a document.
     */
    private void print(Stage owner) {
        // Checked before the job is created, because with no printer installed
        // showPrintDialog simply returns false — indistinguishable from the
        // person pressing Cancel, which would leave the button doing nothing at
        // all with nothing said about why.
        if (Printer.getDefaultPrinter() == null || Printer.getAllPrinters().isEmpty()) {
            Toast.failed("There is no printer to send this to",
                "Windows has no printer installed, or the print spooler is not running. "
                    + "Microsoft Print to PDF counts as one.");
            return;
        }
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null || !job.showPrintDialog(owner)) {
            return;
        }

        VBox copy = build();
        // Off-screen nodes have no styling and no size until they belong to a
        // scene that has been laid out, so the copy is given one.
        Scene scene = new Scene(copy);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        copy.applyCss();
        copy.layout();

        PageLayout page = job.getJobSettings().getPageLayout();
        double scale = Math.min(1.0, Math.min(
            page.getPrintableWidth() / copy.getBoundsInParent().getWidth(),
            page.getPrintableHeight() / copy.getBoundsInParent().getHeight()));
        if (scale < 1.0) {
            copy.getTransforms().add(new Scale(scale, scale));
        }

        if (job.printPage(page, copy)) {
            job.endJob();
            Toast.done("Receipt " + number + " sent to the printer");
        } else {
            job.cancelJob();
            Toast.failed("The receipt could not be printed",
                "The printer refused the page. Try another printer, or Microsoft Print to PDF.");
        }
    }

    private static Window focusedWindow() {
        for (Window window : Window.getWindows()) {
            if (window.isFocused() && window instanceof Stage) {
                return window;
            }
        }
        return null;
    }

    private record Line(String label, String value) {
    }
}
