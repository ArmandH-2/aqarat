package co.syntropyhq.aqarat.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Stores the receipt or transfer slip behind a payment, under
 * {@code uploads/proofs} beside the ownership documents.
 *
 * <p>Validation is {@link DocumentStore}'s, unchanged: the same formats, the
 * same ten-megabyte ceiling, and the same check that a file's first bytes
 * agree with the extension it claims. A payment proof and a title deed are
 * the same kind of artefact — something a member of staff opens later and
 * decides on — so they get the same treatment rather than a second, weaker
 * copy of it.
 *
 * <p>The one difference is the name. A document belongs to a property that
 * already exists, so {@code DocumentStore} can name the file after it. A
 * proof is chosen <em>before</em> its payment row exists — the path is an
 * argument to the insert — so the name is built from who is uploading and
 * when, and the row afterwards points at it.
 */
public final class PaymentProofStore {

    private static final Path PROOFS_DIR = Uploads.root().resolve("proofs");

    private PaymentProofStore() {
    }

    /**
     * Checks a chosen file without copying anything, so an error arrives while
     * the person is still looking at the file chooser.
     *
     * @throws IOException with a message written for the person who picked it
     */
    public static void validate(Path source) throws IOException {
        DocumentStore.validate(source);
    }

    /**
     * Copies a validated file in and returns the path relative to the uploads
     * root, which is what {@code payment.proof_path} stores.
     *
     * <p>The stored name is generated rather than taken from the user, so
     * directory traversal, null bytes and Windows reserved names are
     * structurally impossible instead of merely filtered — the same reasoning
     * as {@code DocumentStore}. An existing file is never overwritten:
     * evidence somebody has already declared a payment against is not ours to
     * replace.
     */
    public static String store(int uploaderUserId, Path source) throws IOException {
        String extension = DocumentStore.validate(source);
        Files.createDirectories(PROOFS_DIR);
        String stem = "pay-" + uploaderUserId + "-" + Instant.now().toEpochMilli();
        Path target = PROOFS_DIR.resolve(stem + "." + extension);
        // Two uploads inside the same millisecond is not a scenario worth a
        // lock, but it is worth not silently failing over, so the name simply
        // takes a suffix until it is free.
        for (int suffix = 2; Files.exists(target); suffix++) {
            target = PROOFS_DIR.resolve(stem + "-" + suffix + "." + extension);
        }
        Files.copy(source, target);
        return "proofs/" + target.getFileName();
    }
}
