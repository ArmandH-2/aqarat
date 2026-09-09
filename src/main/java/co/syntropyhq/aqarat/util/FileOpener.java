package co.syntropyhq.aqarat.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javafx.application.Platform;

/**
 * Hands a stored upload to whatever the operating system opens it with.
 *
 * <p>Two screens need this — the property file opening an ownership document,
 * and the payments queue opening the proof behind a declared payment — and
 * both need the same three refusals handled the same way, so it lives here
 * rather than twice.
 *
 * <p>Always off the UI thread. On Windows the first call to {@code
 * Desktop.open} waits for the handler application to start, which is long
 * enough to freeze a window visibly.
 */
public final class FileOpener {

    private FileOpener() {
    }

    /**
     * @param storedPath a path relative to the uploads root, as the database
     *                   stores it — {@code proofs/pay-314-1757…​.pdf}
     * @param describedAs what to call the file in an error message, in the
     *                    words of the screen asking — "that document", "the
     *                    proof for this payment"
     */
    public static void open(String storedPath, String describedAs) {
        if (storedPath == null || storedPath.isBlank()) {
            AlertUtil.showUndone("There is no file attached to " + describedAs + ".");
            return;
        }
        Path path = Uploads.resolve(storedPath);
        File file = path.toFile();
        if (!file.exists()) {
            AlertUtil.showUndone("That file is no longer in the uploads folder.", storedPath);
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            AlertUtil.showUndone("This system cannot open files from the application.",
                file.getAbsolutePath());
            return;
        }
        Thread opener = new Thread(() -> {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException | UnsupportedOperationException e) {
                Platform.runLater(() -> AlertUtil.showUndone(
                    "Nothing on this computer is set up to open that file.",
                    file.getAbsolutePath()));
            }
        }, "open-upload");
        opener.setDaemon(true);
        opener.start();
    }

    /**
     * Whether a stored value names a file this class could open, as opposed to
     * a reference somebody typed.
     *
     * <p>{@code payment.proof_path} has carried both since before there was an
     * upload for it: a stored file reads {@code proofs/…}, and anything else is
     * a transfer slip number written by hand. The two are shown differently and
     * only one of them is worth offering a button for.
     */
    public static boolean isStoredFile(String storedPath) {
        return storedPath != null && storedPath.startsWith("proofs/");
    }
}
